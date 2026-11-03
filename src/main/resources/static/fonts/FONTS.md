# Web fonts

The two typefaces of the redesign (plan-ui-redesign.md, UI-D4, §6.2) are copied here, not loaded
from a CDN, because the Content-Security-Policy says `font-src 'self'`. Both are open fonts under
the SIL Open Font License 1.1 (`OFL-Baloo2.txt`, `OFL-Nunito.txt`); the licence allows this use
and the redistribution of the subset files, as long as the copyright notice and the licence text
travel with them.

| File | Family | Subset (unicode-range) | Weights | Source | Licence | SHA-256 |
|---|---|---|---|---|---|---|
| `baloo2-latin.woff2` | Baloo 2 (display) | latin | 600–800 | [`ofl/baloo2/Baloo2[wght].ttf`](https://github.com/google/fonts/blob/2fe1e8c6b137fb13041a7cfca6fdcd8a910ea4dc/ofl/baloo2/Baloo2%5Bwght%5D.ttf) at commit `2fe1e8c6` (TTF SHA-256 `d47a6852548059b1db49a1319d06d499d546c3fa2237cf9eee9c43c8abb025c2`) | SIL OFL 1.1 | `2a43451c7cdef60561027b8f6a0dbcf6f689b2b0c87b7069bc001b81b654a4ea` |
| `baloo2-latin-ext.woff2` | Baloo 2 (display) | latin-ext (ő, ű, č, ş …) | 600–800 | as above | SIL OFL 1.1 | `a2ec30a07ab1d2e83e800d4e17572ab216a57ac82ff65bf62fbae675a0b3cc47` |
| `nunito-latin.woff2` | Nunito (body) | latin | 500–800 | [`ofl/nunito/Nunito[wght].ttf`](https://github.com/google/fonts/blob/8b0a1d0f5983c89bc2b93f1b5fb55f9e252744b5/ofl/nunito/Nunito%5Bwght%5D.ttf) at commit `8b0a1d0f` | SIL OFL 1.1 | `f7dd916a9abc8afb4102ae6825c567e9c3b3c1294b078738556cf2874a594d3b` |
| `nunito-latin-ext.woff2` | Nunito (body) | latin-ext (ő, ű, č, ş …) | 500–800 | as above | SIL OFL 1.1 | `e2157ee957b9fc7066b0d0f510b11f896a79601076b6a31af2932ae06faf2a79` |
| `OFL-Baloo2.txt` | licence text | | | `ofl/baloo2/OFL.txt` at `2fe1e8c6` | | `ad09b05dc8bc678c9daf7c4c5f7ef1f55e5726127f4330b2e98e40b9dffcb860` |
| `OFL-Nunito.txt` | licence text | | | `ofl/nunito/OFL.txt` at `8b0a1d0f` | | `580df76c95a1ec5ab878ceb25bb3d85c6a076804e9c970c8c6972aea775fdf65` |

## What was done to the files

The downloaded fonts are the **variable** TTFs from the google/fonts repository, unchanged in
design. Each was reduced for the web with `fontTools` 4.68 (Python), in this order:

1. Baloo 2: `wght` limited to 600–800 (`fontTools.varLib.instancer`); Nunito: `wght` limited to
   500–800.
2. Subsetted to the Unicode ranges Google Fonts uses for `latin` and `latin-ext`, keeping every
   layout feature (`kern`, `liga`, `ss01`, …), the name table (copyright and licence) and no hinting.
3. Written as WOFF2 (`flavor="woff2"`).

Baloo 2 has ő, ű, Ő and Ű in its `latin-ext` subset, so a Hungarian heading is drawn by one family
(#130). It replaced Fredoka, which has no Latin Extended-A glyphs (its ő and ű came from Nunito and
looked thinner). `--font-display` in `app.css` is
`"Baloo 2", "Nunito", "Trebuchet MS", system-ui, sans-serif`; Nunito is only a safety net.
`FontFallbackTest` checks the chain on the stylesheet and `FontFallbackE2ETest` asks Chromium
which fonts drew a heading with ő/ű.

Nunito (the body face) has no `tnum` (tabular figures) feature, so `font-variant-numeric: tabular-nums`
has no effect on body text; digits are proportional. Baloo 2 does have `tnum`, but the app does not use it.

## How to check or rebuild

- Check a file: `sha256sum static/fonts/*.woff2` and compare with the table above.
- Rebuild needs Python with `fonttools` and `brotli`: instantiate and subset the source TTFs as
  described above. Bump the commit in the links and the hashes in the same commit.
- Update procedure is by hand (Dependabot cannot track these files), as for `js/VENDORED.md`.
