# Web fonts

The two typefaces of the redesign (plan-ui-redesign.md, UI-D4, §6.2) are copied here, not loaded
from a CDN, because the Content-Security-Policy says `font-src 'self'`. Both are open fonts under
the SIL Open Font License 1.1 (`OFL-Fredoka.txt`, `OFL-Nunito.txt`); the licence allows this use
and the redistribution of the subset files, as long as the copyright notice and the licence text
travel with them.

| File | Family | Subset (unicode-range) | Weights | Source | Licence | SHA-256 |
|---|---|---|---|---|---|---|
| `fredoka-latin.woff2` | Fredoka (display) | latin | 600–700 | [`ofl/fredoka/Fredoka[wdth,wght].ttf`](https://github.com/google/fonts/blob/a60a77e14f28abd4ef243a1b5dfc48df0cec5205/ofl/fredoka/Fredoka%5Bwdth%2Cwght%5D.ttf) at commit `a60a77e1` | SIL OFL 1.1 | `23bc9b5606795efdc9a864574cf6cb2aff7e2c62a092e5f73d171bc4cbea8e46` |
| `nunito-latin.woff2` | Nunito (body) | latin | 500–800 | [`ofl/nunito/Nunito[wght].ttf`](https://github.com/google/fonts/blob/8b0a1d0f5983c89bc2b93f1b5fb55f9e252744b5/ofl/nunito/Nunito%5Bwght%5D.ttf) at commit `8b0a1d0f` | SIL OFL 1.1 | `f7dd916a9abc8afb4102ae6825c567e9c3b3c1294b078738556cf2874a594d3b` |
| `nunito-latin-ext.woff2` | Nunito (body) | latin-ext (ő, ű, č, ş …) | 500–800 | as above | SIL OFL 1.1 | `e2157ee957b9fc7066b0d0f510b11f896a79601076b6a31af2932ae06faf2a79` |
| `OFL-Fredoka.txt` | licence text | | | `ofl/fredoka/OFL.txt` at `a60a77e1` | | `5c9e7eee5c6b25f4b05b8d53b2e470ea4962f9ced742d044a98f7d95d1375bab` |
| `OFL-Nunito.txt` | licence text | | | `ofl/nunito/OFL.txt` at `8b0a1d0f` | | `580df76c95a1ec5ab878ceb25bb3d85c6a076804e9c970c8c6972aea775fdf65` |

## What was done to the files

The downloaded fonts are the **variable** TTFs from the google/fonts repository, unchanged in
design. Each was reduced for the web with `fontTools` 4.68 (Python), in this order:

1. Fredoka: the `wdth` axis pinned to 100 and `wght` limited to 600–700
   (`fontTools.varLib.instancer`); Nunito: `wght` limited to 500–800.
2. Subsetted to the Unicode ranges Google Fonts uses for `latin` and `latin-ext`, keeping every
   layout feature (`kern`, `liga`, `ss01`, …), the name table (copyright and licence) and no hinting.
3. Written as WOFF2 (`flavor="woff2"`).

Fredoka has **no Latin Extended-A glyphs** in this release (version 2.001): no ő, ű, č, ş, and so
on. There is therefore no `fredoka-latin-ext.woff2`. In headings a Hungarian ő or ű is drawn by the
next family in `--font-display` (`app.css`), which is not a self-hosted font, so it falls back to
the system face. See the R1 notes in `.claude/rules/web-ui.md`.

Neither font has the `tnum` (tabular figures) feature, so `font-variant-numeric: tabular-nums`
has no effect with these files.

## How to check or rebuild

- Check a file: `sha256sum static/fonts/*.woff2` and compare with the table above.
- Rebuild needs Python with `fonttools` and `brotli`: instantiate and subset the source TTFs as
  described above. Bump the commit in the links and the hashes in the same commit.
- Update procedure is by hand (Dependabot cannot track these files), as for `js/VENDORED.md`.
