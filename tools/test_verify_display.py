import unittest

from tools.verify_display import has_vertical_overflow, latest_metrics, screenshot_size, visible_clipping, wm_value


class DisplayVerifierTest(unittest.TestCase):
    def test_parses_physical_and_override_values(self):
        self.assertEqual(wm_value("Physical size: 1920x1200\nOverride size: 1280x800", "Physical size"), "1920x1200")
        self.assertEqual(wm_value("Physical size: 1920x1200", "Override size"), None)

    def test_reads_latest_runtime_metrics(self):
        log = 'I/P4DisplayMetrics: {"windowWidthDp":1200}\nI/P4DisplayMetrics: {"windowWidthDp":700}'
        self.assertEqual(latest_metrics(log)["windowWidthDp"], 700)
        self.assertIsNone(latest_metrics("no metrics"))

    def test_checks_visible_accessibility_bounds(self):
        xml = '<hierarchy><node visible-to-user="true" text="ok" bounds="[1,2][10,20]"/><node visible-to-user="true" text="cut" bounds="[0,2][101,20]"/><node visible-to-user="false" text="hidden" bounds="[-2,0][2,2]"/></hierarchy>'
        self.assertEqual([node["text"] for node in visible_clipping(xml, 100, 100)], ["cut"])

    def test_reads_png_dimensions(self):
        png = b"\x89PNG\r\n\x1a\n" + b"\x00\x00\x00\x0dIHDR" + (1920).to_bytes(4, "big") + (1200).to_bytes(4, "big")
        self.assertEqual(screenshot_size(png), (1920, 1200))

    def test_ignores_rounding_but_detects_vertical_overflow(self):
        self.assertFalse(has_vertical_overflow(828, 824, 1))
        self.assertTrue(has_vertical_overflow(1290, 221, 2))


if __name__ == "__main__":
    unittest.main()
