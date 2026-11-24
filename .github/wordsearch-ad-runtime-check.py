from pathlib import Path
import json
import re
import sqlite3
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET


PACKAGE = "com.drygonkiller.wordsearch"
RESULT_DIR = Path("ad-check")
RESULT_DIR.mkdir(exist_ok=True)
results = {}
BOARD = re.compile(r"(\d+) by \1 letter grid")
BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")
AD_ACTIVITY = PACKAGE + "/com.google.android.gms.ads.AdActivity"


def adb(*args, timeout=30):
    return subprocess.run(
        ["adb", *args], timeout=timeout, check=True, capture_output=True,
    ).stdout


def screen():
    adb("shell", "uiautomator", "dump", "/sdcard/wordsearch-ad-check.xml", timeout=15)
    xml = adb("exec-out", "cat", "/sdcard/wordsearch-ad-check.xml")
    return ET.fromstring(xml)


def node_label(node):
    return node.get("text", "") or node.get("content-desc", "")


def find(tree, value):
    return next((node for node in tree.iter("node") if node_label(node) == value), None)


def rect(node):
    return tuple(map(int, BOUNDS.fullmatch(node.attrib["bounds"]).groups()))


def center(node):
    x1, y1, x2, y2 = rect(node)
    return ((x1 + x2) // 2, (y1 + y2) // 2)


def tap(node):
    x, y = center(node)
    adb("shell", "input", "tap", str(x), str(y))


def wait_for(probe, seconds=20):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            result = probe()
            if result is not None and result is not False:
                return result
        except (ET.ParseError, subprocess.CalledProcessError, subprocess.TimeoutExpired):
            pass
        time.sleep(1)
    return None


def require_text(value, seconds=24):
    node = wait_for(lambda: find(screen(), value), seconds)
    if node is None:
        raise RuntimeError(f"Text did not appear: {value}")
    return node


def snapshot(label):
    (RESULT_DIR / f"{label}.png").write_bytes(adb("exec-out", "screencap", "-p"))
    try:
        ET.ElementTree(screen()).write(RESULT_DIR / f"{label}.xml", encoding="utf-8")
    except Exception:
        pass


def banner_visible():
    views = adb("shell", "dumpsys", "activity", "top").decode(errors="replace")
    measured = re.findall(r"\bAdView\{[^\r\n]*? (-?\d+),(-?\d+)-(-?\d+),(-?\d+)(?: |})", views)
    if any(int(bottom) > int(top) and int(right) > int(left) for left, top, right, bottom in measured):
        return True
    return find(screen(), "Test Ad") is not None and not fullscreen_visible()


def fullscreen_visible():
    activities = adb("shell", "dumpsys", "activity", "activities").decode(errors="replace")
    return any(AD_ACTIVITY in line for line in activities.splitlines() if "ResumedActivity:" in line)


def dismiss_test_ad():
    for _ in range(4):
        if not fullscreen_visible():
            return True
        try:
            for node in screen().iter("node"):
                text = node_label(node).strip().lower()
                if node.get("clickable") == "true" and (
                    text in {"close", "close ad", "dismiss", "dismiss ad", "x", "×"}
                    or text.startswith("close ad ")
                ):
                    tap(node)
                    break
            else:
                adb("shell", "input", "keyevent", "KEYCODE_BACK")
        except Exception:
            adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(2)
    return not fullscreen_visible()


def active_hint():
    with tempfile.TemporaryDirectory() as directory:
        database = Path(directory) / "word-search.db"
        for suffix in ("", "-wal"):
            try:
                data = adb("exec-out", "run-as", PACKAGE, "cat", f"databases/word-search.db{suffix}")
                Path(str(database) + suffix).write_bytes(data)
            except subprocess.CalledProcessError:
                if not suffix:
                    return None
        with sqlite3.connect(database) as connection:
            row = connection.execute("SELECT hint FROM active_puzzle WHERE slot = 1").fetchone()
            return row[0] if row else None


def solve_puzzle():
    tree = screen()
    board_node = next(node for node in tree.iter("node") if BOARD.fullmatch(node.get("content-desc", "")))
    width = int(BOARD.fullmatch(board_node.attrib["content-desc"])[1])
    left, top, right, bottom = rect(board_node)
    letters = {}
    words = []
    for node in tree.iter("node"):
        label = node.get("text", "")
        if re.fullmatch("[A-Z]", label):
            x, y = center(node)
            if left < x < right and top < y < bottom:
                row = (y - top) * width // (bottom - top)
                column = (x - left) * width // (right - left)
                letters[row, column] = (label, x, y)
        elif re.fullmatch("[A-Z]{3,10}", label) and ", found" not in node.get("content-desc", ""):
            words.append(label)
    if len(letters) != width * width or not words:
        raise RuntimeError(f"Only {len(letters)} letters and {len(words)} target words visible")
    for word in dict.fromkeys(words):
        matches = []
        for start_row, start_col in letters:
            for dr, dc in ((-1, -1), (-1, 0), (-1, 1), (0, -1), (0, 1), (1, -1), (1, 0), (1, 1)):
                path = [(start_row + i * dr, start_col + i * dc) for i in range(len(word))]
                if "".join(letters.get(cell, ("", 0, 0))[0] for cell in path) == word:
                    matches.append((letters[path[0]][1:], letters[path[-1]][1:]))
        if not matches:
            raise RuntimeError(f"Puzzle word not found in screen: {word}")
        start, end = matches[0]
        adb("shell", "input", "swipe", *(str(i) for i in (*start, *end)), "420")
        time.sleep(0.18)


def home_ready():
    tree = screen()
    for value in ("Consent", "Agree", "Accept all"):
        button = find(tree, value)
        if button is not None:
            tap(button)
            return None
    return find(tree, "Play")


def main():
    adb("shell", "wm", "size", "1080x2340")
    adb("shell", "wm", "density", "420")
    adb("shell", "input", "keyevent", "82")
    adb("shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity")
    if wait_for(home_ready, 40) is None:
        raise RuntimeError("Fresh install did not reach the Home screen")
    results["home_banner"] = bool(wait_for(banner_visible, 45))
    snapshot("home")
    tap(require_text("Categories"))
    results["categories_banner"] = bool(wait_for(banner_visible, 35))
    snapshot("categories")

    tap(require_text("Settings"))
    tap(require_text("Hide timer"))
    tap(require_text("Home"))
    tap(require_text("Play"))
    tap(require_text("Use free hint"))
    first_hint = wait_for(active_hint, 8)
    snapshot("puzzle")
    results["banner_absent_in_puzzle"] = not banner_visible()
    results["rewarded_shown"] = False
    for _ in range(2):
        tap(require_text("Watch ad for a hint"))
        if wait_for(fullscreen_visible, 12):
            results["rewarded_shown"] = True
            snapshot("rewarded-test-ad")
            time.sleep(37)
            results["rewarded_dismissed"] = dismiss_test_ad()
            new_hint = wait_for(lambda: (hint := active_hint()) and hint != first_hint, 12) if first_hint else False
            results["reward_granted"] = bool(new_hint)
            break
    if fullscreen_visible():
        raise RuntimeError("The rewarded creative stayed open; interstitial playthrough could not continue")
    for count in range(1, 4):
        solve_puzzle()
        shown = bool(wait_for(fullscreen_visible, 7))
        results[f"interstitial_after_{count}"] = shown
        if shown:
            snapshot(f"interstitial-after-{count}")
            time.sleep(8)
            if not dismiss_test_ad():
                raise RuntimeError("Could not close the interstitial creative")
        require_text("Puzzle complete", 15)
        snapshot(f"finish-{count}")
        if count < 3:
            tap(require_text("Next Puzzle"))


try:
    main()
except Exception as failure:
    results["error"] = f"{type(failure).__name__}: {failure}"
finally:
    print(json.dumps(results, indent=2), flush=True)
    (RESULT_DIR / "results.json").write_text(json.dumps(results, indent=2))
    try:
        (RESULT_DIR / "logcat.txt").write_bytes(adb("logcat", "-d", "-t", "2000"))
        snapshot("final")
    except Exception as failure:
        print("Could not capture final emulator diagnostics", failure, flush=True)
expected = {
    "home_banner": True,
    "categories_banner": True,
    "banner_absent_in_puzzle": True,
    "rewarded_shown": True,
    "reward_granted": True,
    "interstitial_after_1": False,
    "interstitial_after_2": False,
    "interstitial_after_3": True,
}
if "error" in results or any(results.get(key) != value for key, value in expected.items()):
    raise SystemExit(1)
