from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = "com.drygonkiller.wordsearch"
PLAYER = PACKAGE + "/.MainActivity"
RESULT_DIR = Path("play-phone-screenshots")
RESULT_DIR.mkdir(exist_ok=True)
BOARD = re.compile(r"(\d+) by \1 letter grid")
BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")

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


def solve_puzzle(limit=None):
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
    for word in list(dict.fromkeys(words))[:limit]:
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

def capture(filename, expected):
    require_text(expected)
    time.sleep(0.5)
    raw = adb("exec-out", "screencap", "-p")
    if not raw.startswith(b"\x89PNG\r\n\x1a\n"):
        raise RuntimeError(f"Screenshot capture failed for {filename}")
    (RESULT_DIR / filename).write_bytes(raw)
    print(f"Captured {filename}", flush=True)


def main():
    adb("shell", "wm", "size", "1080x1920")
    adb("shell", "wm", "density", "420")
    adb("shell", "settings", "put", "global", "sysui_demo_allowed", "1")
    adb("shell", "am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", "enter")
    adb("shell", "am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", "clock", "-e", "hhmm", "0941")
    adb("shell", "am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", "battery", "-e", "level", "100", "-e", "plugged", "false")
    adb("shell", "cmd", "connectivity", "airplane-mode", "enable")
    adb("shell", "svc", "wifi", "disable")
    adb("shell", "svc", "data", "disable")
    adb("shell", "input", "keyevent", "82")
    adb("shell", "am", "start", "-W", "-n", PLAYER)

    tap(require_text("Play"))
    require_text("Find the words")
    solve_puzzle()
    tap(require_text("Home", 30))

    tap(require_text("Daily Challenge"))
    require_text("Find the words")
    solve_puzzle()
    tap(require_text("Home", 30))

    tap(require_text("Stats"))
    require_text("Streak calendar")
    adb("shell", "input", "swipe", "540", "1530", "540", "670", "650")
    if not wait_for(lambda: any("today, Daily Challenge completed" in node.get("content-desc", "")
                               for node in screen().iter("node")), 12):
        raise RuntimeError("The completed Daily Challenge is not visible in the calendar")
    capture("06-streak-calendar.png", "Streak calendar")


if __name__ == "__main__":
    try:
        main()
    finally:
        Path("capture-last-screen.png").write_bytes(adb("exec-out", "screencap", "-p"))
