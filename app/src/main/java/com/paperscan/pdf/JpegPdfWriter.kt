package com.paperscan.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import com.paperscan.model.OcrTextLine
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Minimal sequential PDF writer for PaperScan exports.
 *
 * Android's PdfDocument has no public control for embedded image encoding. This writer
 * embeds one opaque JPEG image per page so the scan output has a bounded raster size and
 * predictable DCT compression, while keeping the existing PDF point sizing and page order.
 * OCR text uses self-contained, invisible Type 3 fonts: no device font or unlicensed CJK
 * font file is required, and ToUnicode provides the actual searchable characters.
 */
internal class JpegPdfWriter(
    output: File,
    private val pageCount: Int,
    private val settings: PdfExportSettings = PdfExportSettings()
) : AutoCloseable {
    private val countingOutput = CountingOutputStream(FileOutputStream(output))
    private val objectOffsets = mutableListOf(0L, 0L, 0L)
    private val pageObjects = mutableListOf<Int>()
    private var nextPageIndex = 0
    private var finished = false

    init {
        require(pageCount > 0) { "PDFページ数が不正です。" }
        writeAscii("%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n")
        writeObject(CATALOG_OBJECT) {
            writeAscii("<< /Type /Catalog /Pages $PAGES_OBJECT 0 R >>\n")
        }
    }

    fun writePage(source: Bitmap, ocrLines: List<OcrTextLine> = emptyList()) {
        check(!finished) { "PDFはすでに完了しています。" }
        check(nextPageIndex < pageCount) { "PDFページ数が多すぎます。" }

        val pageSize = PdfPageSizer.pageSizeFor(
            source.width,
            source.height,
            settings.paperSize,
            settings.orientation
        )
        val pageIndex = nextPageIndex++
        val pageObject = allocateObject().also(pageObjects::add)
        val imageObject = allocateObject()
        val contentObject = allocateObject()
        val text = encodeText(ocrLines)
        val blankGlyphObject = if (text.fonts.isNotEmpty()) allocateObject() else null
        val fontObjects = text.fonts.map { allocateObject() to allocateObject() }
        val rasterSize = PdfRasterSizer.sizeFor(
            pageSize,
            source.width,
            source.height,
            settings.quality.targetDpi
        )
        val jpeg = encodeOpaqueJpeg(source, rasterSize, settings.quality.jpegQuality, settings.colorMode)
        try {
            val draw = PdfPageSizer.fitRect(pageSize, rasterSize.widthPixels, rasterSize.heightPixels)
            writeObject(pageObject) {
                val fontResources = fontObjects.mapIndexed { index, pair -> "/F$index ${pair.first} 0 R" }.joinToString(" ")
                writeAscii(
                    "<< /Type /Page /Parent $PAGES_OBJECT 0 R /MediaBox [0 0 ${pageSize.widthPoints} ${pageSize.heightPoints}] " +
                        "/Resources << /XObject << /Im$pageIndex $imageObject 0 R >> /Font << $fontResources >> >> " +
                        "/Contents $contentObject 0 R >>\n"
                )
            }
            writeStreamObject(
                objectNumber = imageObject,
                dictionary = "<< /Type /XObject /Subtype /Image /Width ${rasterSize.widthPixels} /Height ${rasterSize.heightPixels} " +
                    "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size} >>",
                bytes = jpeg
            )
            val imageContent = String.format(
                Locale.US,
                "q\n%.4f 0 0 %.4f %.4f %.4f cm\n/Im%d Do\nQ\n",
                draw.right - draw.left,
                draw.bottom - draw.top,
                draw.left,
                pageSize.heightPoints - draw.bottom,
                pageIndex
            )
            val textContent = createInvisibleTextContent(pageSize, draw, text.lines)
            val content = (imageContent + textContent).toByteArray(StandardCharsets.US_ASCII)
            writeStreamObject(
                objectNumber = contentObject,
                dictionary = "<< /Length ${content.size} >>",
                bytes = content
            )

            blankGlyphObject?.let { glyphObject ->
                val glyph = "1000 0 d0\n".toByteArray(StandardCharsets.US_ASCII)
                writeStreamObject(glyphObject, "<< /Length ${glyph.size} >>", glyph)
                text.fonts.forEachIndexed { index, font ->
                    val (fontObject, toUnicodeObject) = fontObjects[index]
                    val names = List(font.codePoints.size) { "/blank" }.joinToString(" ")
                    val widths = List(font.codePoints.size) { "1000" }.joinToString(" ")
                    writeObject(fontObject) {
                        writeAscii(
                            "<< /Type /Font /Subtype /Type3 /Name /F$index /FontBBox [0 0 1000 1000] " +
                                "/FontMatrix [0.001 0 0 0.001 0 0] " +
                                "/CharProcs << /.notdef $glyphObject 0 R /blank $glyphObject 0 R >> " +
                                "/Encoding << /Type /Encoding /Differences [1 $names] >> " +
                                "/FirstChar 1 /LastChar ${font.codePoints.size} /Widths [$widths] " +
                                "/ToUnicode $toUnicodeObject 0 R >>\n"
                        )
                    }
                    val cmap = createToUnicodeCMap(font.codePoints)
                    writeStreamObject(toUnicodeObject, "<< /Length ${cmap.size} >>", cmap)
                }
            }
        } finally {
            jpeg.fill(0)
        }
    }

    fun finish() {
        check(!finished) { "PDFはすでに完了しています。" }
        check(nextPageIndex == pageCount) { "PDFページ数が不足しています。" }
        writeObject(PAGES_OBJECT) {
            val kids = pageObjects.joinToString(" ") { "$it 0 R" }
            writeAscii("<< /Type /Pages /Kids [$kids] /Count $pageCount >>\n")
        }
        val xrefOffset = countingOutput.count
        val objectCount = objectOffsets.size - 1
        check((1..objectCount).all { objectOffsets[it] > 0L }) { "PDFオブジェクトが不足しています。" }
        writeAscii("xref\n0 ${objectCount + 1}\n0000000000 65535 f \n")
        (1..objectCount).forEach { objectNumber ->
            writeAscii(String.format(Locale.US, "%010d 00000 n \n", objectOffsets[objectNumber]))
        }
        writeAscii("trailer\n<< /Size ${objectCount + 1} /Root $CATALOG_OBJECT 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        finished = true
    }

    override fun close() {
        countingOutput.close()
    }

    private fun allocateObject(): Int = objectOffsets.size.also { objectOffsets.add(0L) }

    private fun createInvisibleTextContent(
        pageSize: PdfPageSize,
        draw: PdfDrawRect,
        lines: List<EncodedLine>
    ): String {
        val commands = StringBuilder()
        lines.forEach { encodedLine ->
            val bounds = encodedLine.line.bounds
            val x = draw.left + bounds.left * (draw.right - draw.left)
            val top = draw.top + bounds.top * (draw.bottom - draw.top)
            val bottom = draw.top + bounds.bottom * (draw.bottom - draw.top)
            val width = (bounds.right - bounds.left) * (draw.right - draw.left)
            val height = (bottom - top).coerceAtLeast(1f)
            val fontSize = height.coerceAtLeast(MIN_TEXT_SIZE_POINTS)
            val total = encodedLine.runs.sumOf { it.codes.size }
            if (total == 0) return@forEach
            val horizontalScale = (width / (fontSize * total)).coerceIn(0.01f, 100f)
            var offset = 0
            encodedLine.runs.forEach { run ->
                val encoded = run.codes.joinToString("") { "%02X".format(it) }
                commands.append(String.format(
                    Locale.US,
                    "BT /F%d %.4f Tf 3 Tr %.6f 0 0 1 %.4f %.4f Tm <%s> Tj ET\n",
                    run.fontIndex,
                    fontSize,
                    horizontalScale,
                    x + width * offset / total,
                    pageSize.heightPoints - bottom,
                    encoded
                ))
                offset += run.codes.size
            }
        }
        return commands.toString()
    }

    /** Simple PDF fonts have one-byte codes, so a page may need several fonts for CJK text. */
    private fun encodeText(lines: List<OcrTextLine>): EncodedText {
        val fonts = mutableListOf<EncodedFont>()
        val encodedLines = lines.sortedWith(compareBy<OcrTextLine> { it.bounds.top }.thenBy { it.bounds.left })
            .mapNotNull { line ->
                val runs = mutableListOf<EncodedRun>()
                line.text.codePoints().forEach { codePoint ->
                    val fontIndex = fonts.indexOfFirst { codePoint in it.codePoints }.takeIf { it >= 0 }
                        ?: run {
                            if (fonts.isEmpty() || fonts.last().codePoints.size == MAX_CODES_PER_FONT) {
                                fonts += EncodedFont()
                            }
                            fonts.lastIndex
                        }
                    val font = fonts[fontIndex]
                    val code = font.codePoints.getOrPut(codePoint) { font.codePoints.size + 1 }
                    if (runs.lastOrNull()?.fontIndex == fontIndex) {
                        runs.last().codes += code
                    } else {
                        runs += EncodedRun(fontIndex, mutableListOf(code))
                    }
                }
                runs.takeIf { it.isNotEmpty() }?.let { EncodedLine(line, it) }
            }
        return EncodedText(fonts, encodedLines)
    }

    private fun createToUnicodeCMap(codePoints: Map<Int, Int>): ByteArray {
        val entries = codePoints.map { (codePoint, code) ->
            val unicodeHex = String(Character.toChars(codePoint)).toByteArray(StandardCharsets.UTF_16BE)
                .joinToString("") { byte -> "%02X".format(byte.toInt() and 0xFF) }
            "<%02X> <$unicodeHex>".format(code)
        }
        val blocks = entries.chunked(100).joinToString("\n") { block ->
            "${block.size} beginbfchar\n${block.joinToString("\n")}\nendbfchar"
        }
        val cmap = """
            /CIDInit /ProcSet findresource begin
            12 dict begin
            begincmap
            /CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def
            /CMapName /PaperScan-Identity-UCS def
            /CMapType 2 def
            1 begincodespacerange
            <01> <FF>
            endcodespacerange
            $blocks
            endcmap
            CMapName currentdict /CMap defineresource pop
            end
            end
        """.trimIndent()
        return cmap.toByteArray(StandardCharsets.US_ASCII)
    }

    private fun writeObject(objectNumber: Int, body: () -> Unit) {
        objectOffsets[objectNumber] = countingOutput.count
        writeAscii("$objectNumber 0 obj\n")
        body()
        writeAscii("endobj\n")
    }

    private fun writeStreamObject(objectNumber: Int, dictionary: String, bytes: ByteArray) {
        writeObject(objectNumber) {
            writeAscii("$dictionary\nstream\n")
            countingOutput.write(bytes)
            writeAscii("\nendstream\n")
        }
    }

    private fun writeAscii(value: String) {
        countingOutput.write(value.toByteArray(StandardCharsets.US_ASCII))
    }

    private fun encodeOpaqueJpeg(
        source: Bitmap,
        rasterSize: PdfRasterSize,
        jpegQuality: Int,
        colorMode: PdfColorMode
    ): ByteArray {
        val raster = Bitmap.createBitmap(rasterSize.widthPixels, rasterSize.heightPixels, Bitmap.Config.ARGB_8888)
        try {
            val colorFilter = when (colorMode) {
                PdfColorMode.AUTO, PdfColorMode.COLOR -> null
                PdfColorMode.GRAYSCALE -> ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
                PdfColorMode.BLACK_WHITE -> ColorMatrixColorFilter(
                    ColorMatrix(
                        floatArrayOf(
                            1.196f, 2.348f, 0.456f, 0f, -510f,
                            1.196f, 2.348f, 0.456f, 0f, -510f,
                            1.196f, 2.348f, 0.456f, 0f, -510f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            Canvas(raster).apply {
                drawColor(Color.WHITE)
                drawBitmap(
                    source,
                    null,
                    Rect(0, 0, rasterSize.widthPixels, rasterSize.heightPixels),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.colorFilter = colorFilter }
                )
            }
            return ByteArrayOutputStream().use { encoded ->
                check(raster.compress(Bitmap.CompressFormat.JPEG, jpegQuality, encoded)) {
                    "PDF用JPEGを作成できませんでした。"
                }
                encoded.toByteArray()
            }
        } finally {
            raster.recycle()
        }
    }

    private companion object {
        const val CATALOG_OBJECT = 1
        const val PAGES_OBJECT = 2
        const val MAX_CODES_PER_FONT = 255
        const val MIN_TEXT_SIZE_POINTS = 2f
    }

    private data class EncodedFont(val codePoints: MutableMap<Int, Int> = linkedMapOf())
    private data class EncodedRun(val fontIndex: Int, val codes: MutableList<Int>)
    private data class EncodedLine(val line: OcrTextLine, val runs: List<EncodedRun>)
    private data class EncodedText(val fonts: List<EncodedFont>, val lines: List<EncodedLine>)
}

private class CountingOutputStream(private val delegate: OutputStream) : OutputStream() {
    var count: Long = 0
        private set

    override fun write(value: Int) {
        delegate.write(value)
        count++
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        delegate.write(bytes, offset, length)
        count += length
    }

    override fun flush() = delegate.flush()

    override fun close() = delegate.close()
}
