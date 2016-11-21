/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.badlogic.gdx.tools.hiero;

import java.awt.Font;
import java.awt.Rectangle;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.tools.hiero.unicodefont.Glyph;
import com.badlogic.gdx.tools.hiero.unicodefont.GlyphPage;
import com.badlogic.gdx.tools.hiero.unicodefont.UnicodeFont;
import com.badlogic.gdx.utils.IntIntMap;

import java.io.Writer;
import java.io.OutputStreamWriter;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.JsonWriter.OutputType;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonValue.ValueType;

/** @author Nathan Sweet */
public class BMFontUtil {
	private final UnicodeFont unicodeFont;

	public BMFontUtil (UnicodeFont unicodeFont) {
		this.unicodeFont = unicodeFont;
	}

	public void save (File outputBMFontFile) throws IOException {
		File outputDir = outputBMFontFile.getParentFile();
		String outputName = outputBMFontFile.getName();
		int dotIndex = outputName.lastIndexOf('.');
		if (dotIndex != -1) outputName = outputName.substring(0, dotIndex);
		
		// Always include space and the missing gyph.
		getGlyph(' ');
		getGlyph('\u0000');
		unicodeFont.loadGlyphs();
		
		Font font = unicodeFont.getFont();
		int pageWidth = unicodeFont.getGlyphPageWidth();
		int pageHeight = unicodeFont.getGlyphPageHeight();
		
		JsonValue fileJson = new JsonValue(ValueType.object);
		fileJson.addChild("pageWidth", new JsonValue(unicodeFont.getGlyphPageWidth()));
		fileJson.addChild("pageHeight", new JsonValue(unicodeFont.getGlyphPageHeight()));
		fileJson.addChild("base", new JsonValue(unicodeFont.getAscent()));
		fileJson.addChild("lineHeight", new JsonValue(unicodeFont.getLineHeight()));
		JsonValue pagesJson = new JsonValue(ValueType.object);
		fileJson.addChild("pages", pagesJson);
		
		int pageIndex = 0, glyphCount = 0;
		for (Iterator pageIter = unicodeFont.getGlyphPages().iterator(); pageIter.hasNext();) {
			GlyphPage page = (GlyphPage)pageIter.next();
			String fileName;
			if (pageIndex == 0 && !pageIter.hasNext())
				fileName = outputName + ".png";
			else
				fileName = outputName + (pageIndex + 1) + ".png";
			JsonValue pageJson = new JsonValue(ValueType.object);
			pagesJson.addChild(fileName, pageJson);
			List<Glyph> glyphs = page.getGlyphs();
			for (Iterator glyphIter = page.getGlyphs().iterator(); glyphIter.hasNext();) {
				Glyph glyph = (Glyph)glyphIter.next();
				JsonValue glyphJson = new JsonValue(ValueType.object);
				pageJson.addChild(String.valueOf(glyph.getCodePoint()), glyphJson);
				glyphJson.addChild("x", new JsonValue((int)(glyph.getU() * pageWidth)));
				glyphJson.addChild("y", new JsonValue((int)(glyph.getV() * pageHeight)));
				glyphJson.addChild("w", new JsonValue(glyph.getWidth()));
				glyphJson.addChild("h", new JsonValue(glyph.getHeight()));
				glyphJson.addChild("xo", new JsonValue(glyph.getXOffset()));
				glyphJson.addChild("yo", new JsonValue(glyph.getYOffset()));
				glyphJson.addChild("xa", new JsonValue(glyph.getXAdvance()));
			}
			pageIndex++;
		}

		Writer writer = new OutputStreamWriter(new FileOutputStream(outputBMFontFile, false), "UTF-8");
		writer.write(fileJson.prettyPrint(OutputType.json, 4));
		writer.close();

		int width = unicodeFont.getGlyphPageWidth();
		int height = unicodeFont.getGlyphPageHeight();
		IntBuffer buffer = BufferUtils.createIntBuffer(width * height);
		BufferedImage pageImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		int[] row = new int[width];

		pageIndex = 0;
		for (Iterator pageIter = unicodeFont.getGlyphPages().iterator(); pageIter.hasNext();) {
			GlyphPage page = (GlyphPage)pageIter.next();
			String fileName;
			if (pageIndex == 0 && !pageIter.hasNext())
				fileName = outputName + ".png";
			else
				fileName = outputName + (pageIndex + 1) + ".png";

			page.getTexture().bind();
			buffer.clear();
			GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, buffer);
			WritableRaster raster = pageImage.getRaster();
			for (int y = 0; y < height; y++) {
				buffer.get(row);
				raster.setDataElements(0, y, width, 1, row);
			}
			File imageOutputFile = new File(outputDir, fileName);
			ImageIO.write(pageImage, "png", imageOutputFile);

			pageIndex++;
		}
	}

	/** @return May be null. */
	private Glyph getGlyph (char c) {
		char[] chars = {c};
		GlyphVector vector = unicodeFont.getFont().layoutGlyphVector(GlyphPage.renderContext, chars, 0, chars.length,
			Font.LAYOUT_LEFT_TO_RIGHT);
		Rectangle bounds = vector.getGlyphPixelBounds(0, GlyphPage.renderContext, 0, 0);
		return unicodeFont.getGlyph(vector.getGlyphCode(0), c, bounds, vector, 0);
	}

	void writeGlyph (PrintStream out, int pageWidth, int pageHeight, int pageIndex, Glyph glyph) {
		out.println("char id=" + String.format("%-6s", glyph.getCodePoint()) //
			+ "x=" + String.format("%-5s", (int)(glyph.getU() * pageWidth)) //
			+ "y=" + String.format("%-5s", (int)(glyph.getV() * pageHeight)) //
			+ "width=" + String.format("%-5s", glyph.getWidth()) //
			+ "height=" + String.format("%-5s", glyph.getHeight()) //
			+ "xoffset=" + String.format("%-5s", glyph.getXOffset()) //
			+ "yoffset=" + String.format("%-5s", glyph.getYOffset()) //
			+ "xadvance=" + String.format("%-5s", glyph.getXAdvance()) //
			+ "page=" + String.format("%-5s", pageIndex) //
			+ "chnl=0 ");
	}

	private int getGlyphCode (Font font, int codePoint) {
		char[] chars = Character.toChars(codePoint);
		GlyphVector vector = font.layoutGlyphVector(GlyphPage.renderContext, chars, 0, chars.length, Font.LAYOUT_LEFT_TO_RIGHT);
		return vector.getGlyphCode(0);
	}
}
