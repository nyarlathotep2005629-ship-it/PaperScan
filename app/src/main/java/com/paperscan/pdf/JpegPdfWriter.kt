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
 */
internal class JpegPdfWriter(
    output: File,
    private val pageCount: Int,
    private val settings: PdfExportSettings = PdfExportSettings()
) : AutoCloseable {
    private val countingOutput = CountingOutputStream(FileOutputStream(output))
    private val objectOffsets = LongArray(3 + pageCount * OBJECTS_PER_PAGE)
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
        val pageObject = pageObjectNumber(pageIndex)
        val imageObject = pageObject + 1
        val contentObject = pageObject + 2
        val type0FontObject = pageObject + 3
        val cidFontObject = pageObject + 4
        val fontDescriptorObject = pageObject + 5
        val toUnicodeObject = pageObject + 6
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
                writeAscii(
                    "<< /Type /Page /Parent $PAGES_OBJECT 0 R /MediaBox [0 0 ${pageSize.widthPoints} ${pageSize.heightPoints}] " +
                        "/Resources << /XObject << /Im$pageIndex $imageObject 0 R >> /Font << /F0 $type0FontObject 0 R >> >> " +
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
            val textContent = createInvisibleTextContent(pageSize, draw, ocrLines)
            val content = (imageContent + textContent).toByteArray(StandardCharsets.US_ASCII)
            writeStreamObject(
                objectNumber = contentObject,
                dictionary = "<< /Length ${content.size} >>",
                bytes = content
            )

            writeObject(type0FontObject) {
                writeAscii(
                    "<< /Type /Font /Subtype /Type0 /BaseFont /HeiseiMin-W3 /Encoding /Identity-H " +
                        "/DescendantFonts [$cidFontObject 0 R] /ToUnicode $toUnicodeObject 0 R >>\n"
                )
            }
            val glyphCount = ocrLines.sumOf { it.text.codePointCount(0, it.text.length) }
            require(glyphCount < 0xFFFF) { "OCRテキストが1ページのPDF文字レイヤー上限を超えています。" }
            val toUnicode = createToUnicodeCMap(ocrLines)
            writeObject(cidFontObject) {
                val widths = List(glyphCount) { "1000" }.joinToString(" ")
                val widthTable = if (glyphCount == 0) "" else " /W [1 [$widths]]"
                writeAscii(
                    "<< /Type /Font /Subtype /CIDFontType2 /BaseFont /HeiseiMin-W3 " +
                        "/CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >> " +
                        "/FontDescriptor $fontDescriptorObject 0 R /DW 1000$widthTable /CIDToGIDMap /Identity >>\n"
                )
            }
            writeObject(fontDescriptorObject) {
                writeAscii(
                    "<< /Type /FontDescriptor /FontName /HeiseiMin-W3 /Flags 4 /FontBBox [0 -250 1000 1000] " +
                        "/ItalicAngle 0 /Ascent 880 /Descent -120 /CapHeight 700 /StemV 80 >>\n"
                )
            }
            writeStreamObject(
                objectNumber = toUnicodeObject,
                dictionary = "<< /Length ${toUnicode.size} >>",
                bytes = toUnicode
            )
        } finally {
            jpeg.fill(0)
        }
    }

    fun finish() {
        check(!finished) { "PDFはすでに完了しています。" }
        check(nextPageIndex == pageCount) { "PDFページ数が不足しています。" }
        writeObject(PAGES_OBJECT) {
            val kids = (0 until pageCount).joinToString(" ") { "${pageObjectNumber(it)} 0 R" }
            writeAscii("<< /Type /Pages /Kids [$kids] /Count $pageCount >>\n")
        }
        val xrefOffset = countingOutput.count
        val objectCount = objectOffsets.size - 1
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

    private fun pageObjectNumber(pageIndex: Int): Int = FIRST_PAGE_OBJECT + pageIndex * OBJECTS_PER_PAGE

    private fun createInvisibleTextContent(
        pageSize: PdfPageSize,
        draw: PdfDrawRect,
        lines: List<OcrTextLine>
    ): String {
        var nextCode = 1
        val commands = StringBuilder()
        lines.sortedWith(compareBy<OcrTextLine> { it.bounds.top }.thenBy { it.bounds.left }).forEach { line ->
            val codePoints = line.text.codePoints().toArray()
            if (codePoints.isEmpty()) return@forEach
            val encoded = buildString(codePoints.size * 4) {
                repeat(codePoints.size) { append("%04X".format(nextCode++)) }
            }
            val x = draw.left + line.bounds.left * (draw.right - draw.left)
            val top = draw.top + line.bounds.top * (draw.bottom - draw.top)
            val bottom = draw.top + line.bounds.bottom * (draw.bottom - draw.top)
            val width = (line.bounds.right - line.bounds.left) * (draw.right - draw.left)
            val height = (bottom - top).coerceAtLeast(1f)
            val fontSize = height.coerceAtLeast(MIN_TEXT_SIZE_POINTS)
            val horizontalScale = (width / (fontSize * codePoints.size)).coerceIn(0.01f, 100f)
            commands.append(String.format(
                Locale.US,
                "BT /F0 %.4f Tf 3 Tr %.6f 0 0 1 %.4f %.4f Tm <%s> Tj ET\n",
                fontSize,
                horizontalScale,
                x,
                pageSize.heightPoints - bottom,
                encoded
            ))
        }
        return commands.toString()
    }

    private fun createToUnicodeCMap(lines: List<OcrTextLine>): ByteArray {
        val entries = buildList {
            var nextCode = 1
            lines.sortedWith(compareBy<OcrTextLine> { it.bounds.top }.thenBy { it.bounds.left }).forEach { line ->
                line.text.codePoints().toArray().forEach { codePoint ->
                    val unicodeHex = String(Character.toChars(codePoint)).toByteArray(StandardCharsets.UTF_16BE)
                        .joinToString("") { byte -> "%02X".format(byte.toInt() and 0xFF) }
                    add("<%04X> <$unicodeHex>".format(nextCode++))
                }
            }
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
            <0000> <FFFF>
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
        const val FIRST_PAGE_OBJECT = 3
        const val OBJECTS_PER_PAGE = 7
        const val MIN_TEXT_SIZE_POINTS = 2f
    }
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
