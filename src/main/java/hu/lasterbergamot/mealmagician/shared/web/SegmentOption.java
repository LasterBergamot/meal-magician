package hu.lasterbergamot.mealmagician.shared.web;

/**
 * One segment of the {@code ui/_segmented} fragment (the segmented control): what it is called,
 * which value it stands for and the address it links to.
 *
 * <p>The {@code href} is a real link, so the control works before any JavaScript has run. When the
 * control is given an htmx target, the same address is fetched and swapped in place instead, so a
 * segment serves one URL for both a browser and htmx. The label is already translated.
 *
 * @param value the identifier of the segment, compared with the selected value
 * @param label the text shown on the segment, already translated
 * @param href the address the segment links to, and that htmx fetches with a target
 */
public record SegmentOption(String value, String label, String href) {}
