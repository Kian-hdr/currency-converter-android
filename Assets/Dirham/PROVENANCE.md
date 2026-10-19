# Official UAE dirham symbol

The SVG and Android vector preserve the Central Bank of the UAE's official new dirham D symbol, extracted directly from the bank's **Dirham Currency Symbol Guideline, version1.0**, physical PDF page4. This is original vector geometry, not a raster trace or a generated approximation.

Source: https://www.centralbank.ae/media/w3ansspi/dirham_currency_symbol_guidelines_en_21-may.pdf

The PDF links an SVG download archive, but that archive returned HTTP403. The PDF itself is available, and its isolated symbol is a single48-segment filled path. `Provenance.json` records the source PDF hash, exact path identity, bounds and extraction method. The extracted path has three interior contours and the outer contour, retaining nonzero winding.

`UAE_Dirham_Official.svg` is editable source. `ic_dirham.xml` uses the identical translated path with a white fill for native runtime tinting. Keep the aspect ratio1.149795, two horizontal bars and original shape. Guidelines specify symbol to the left of the amount, with matching numeral height, minimum12screenpixels and sufficient spacing. Use a white symbol for functional dark-interface display, as the guideline primarily specifies black or white.

UAE dirham character metadata is Unicode18 U+20C3; the bundled vector avoids relying on Android16 font coverage.
