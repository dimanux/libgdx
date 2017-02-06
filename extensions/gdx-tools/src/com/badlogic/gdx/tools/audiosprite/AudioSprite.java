package com.badlogic.gdx.tools.audiosprite;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import com.badlogic.gdx.tools.FileProcessor;
import com.badlogic.gdx.tools.FileProcessor.Entry;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.JsonWriter.OutputType;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonValue.ValueType;

public class AudioSprite {
	static public void main (String[] args) throws Exception {
		String inputDir = null, outputDir = null, packFileName = "sounds", wavFileName = "sounds";
		float silenceDuration = 0.2f;
		for (int i = 0; i < args.length; i++) {
			final String param = args[i];
			final boolean more = i < args.length - 1;
			if (param.equals("-h") || param.equals("--help")) {
				System.err.println("Usage: -i inputDir -o outputPackFile [-s silenceDuration]");
				System.exit(0);
				return;
			} else if (more && (param.equals("-i") || param.equals("--input"))) {
				inputDir = args[++i];
			} else if (more && (param.equals("-o") || param.equals("--output"))) {
				packFileName = args[++i];
			} else if (more && (param.equals("-s") || param.equals("--silence"))) {
				silenceDuration = Float.parseFloat(args[++i]);
			} else {
				System.err.println("Unknown parameter: " + param);
				System.exit(3);
				return;
			}
		}
		
		int dotIndex = packFileName.lastIndexOf('.');
		if (dotIndex == -1) {
			wavFileName = packFileName + ".wav";
		} else {
			wavFileName = packFileName.substring(0, dotIndex) + ".wav";
		}
		
		FileProcessor fileProcessor = new FileProcessor() {
			protected void processFile (Entry entry) {
				addProcessedFile(entry);
			}
		};
		fileProcessor.addInputSuffix(".wav");
		File inputDirFile = new File(inputDir);
		ArrayList<Entry> files = fileProcessor.process(inputDirFile, null);
		String rootPath = inputDirFile.getAbsolutePath().replace('\\', '/');
		if (!rootPath.endsWith("/")) rootPath += "/";
		
		File outputFile = new File(wavFileName);
		outputDir = outputFile.getParentFile().getAbsolutePath();
		outputFile.getParentFile().mkdirs();
		AudioFormat format = null;
		ArrayList<AudioInputStream> inStreams = new ArrayList<AudioInputStream>();
		float offset = 0.0f;
		JsonValue fileJson = new JsonValue(ValueType.object);
		JsonValue pageJson = new JsonValue(ValueType.object);
		fileJson.addChild(outputFile.getName(), pageJson);
		for (Entry file : files) {
			if (!file.inputFile.equals(outputFile)) {
				AudioInputStream inStream = AudioSystem.getAudioInputStream(file.inputFile);
				if (format == null) {
					format = inStream.getFormat();
				}
				AudioInputStream silenceStream = new AudioInputStream(new ByteArrayInputStream(new byte[(int) (format.getFrameSize() * format.getFrameRate() * silenceDuration)]), format, (long) (format.getFrameRate() * silenceDuration));
				if (!format.matches(inStream.getFormat()))
				{
					inStream = AudioSystem.getAudioInputStream(format, inStream);
					if (inStream == null)
					{
						System.out.println("Can't convert " + file.inputFile.getAbsolutePath() + " to format " + format);
						continue;
					}
				}
				JsonValue soundJson = new JsonValue(ValueType.object);
				String name = file.inputFile.getAbsolutePath().replace('\\', '/');
				name = name.substring(rootPath.length());
				pageJson.addChild(name, soundJson);
				soundJson.addChild("s", new JsonValue(Math.floor(offset * 1000) / 1000));
				offset += (float)inStream.getFrameLength() / (float)format.getFrameRate();
				soundJson.addChild("e", new JsonValue(Math.floor(offset * 1000) / 1000));
				inStreams.add(inStream);
				offset += (float)silenceStream.getFrameLength() / (float)format.getFrameRate();
				inStreams.add(silenceStream);
			}
		}
		if ((format != null) && (inStreams.size() > 0)) {
			inStreams.remove(inStreams.size() - 1);
			AudioInputStream outStream = new AudioSpriteInputStream(format, inStreams);
			AudioSystem.write(outStream, AudioFileFormat.Type.WAVE, outputFile);
		}
		Writer writer = new OutputStreamWriter(new FileOutputStream(new File(packFileName), false), "UTF-8");
		writer.write(fileJson.prettyPrint(OutputType.json, 4));
		writer.close();
	}
	
	static public class AudioSpriteInputStream extends AudioInputStream
	{
		private ArrayList<AudioInputStream> streams;
		private	int current;
		
		public AudioSpriteInputStream(AudioFormat format, ArrayList<AudioInputStream> inStreams)
		{
			super(new ByteArrayInputStream(new byte[0]), format, AudioSystem.NOT_SPECIFIED);
			streams = inStreams;
			current = 0;
		}
	
		public int read() throws IOException
		{
			AudioInputStream stream = streams.get(current);
			int	value = stream.read();
			if (value == -1)
			{
				if (++current < streams.size())
				{
					return read();
				}
				else
				{
					return -1;
				}
			}
			else
			{
				return value;
			}
		}

		public int read(byte[] b, int off, int len) throws IOException
		{
			AudioInputStream stream = streams.get(current);
			int	readed = stream.read(b, off, len);
			if (readed == -1)
			{
				if (++current < streams.size())
				{
					return read(b, off, len);
				}
				else
				{
					return -1;
				}
			}
			else
			{
				return readed;
			}
		}
		
		public long skip(long lLength) throws IOException
		{
			throw new IOException("skip() is not implemented in class AudioSpriteInputStream.");
		}
		
		public int available() throws IOException
		{
			return streams.get(current).available();
		}
		
		public void close() throws IOException
		{
			for (AudioInputStream stream : streams) {
				stream.close();
			}
			super.close();
		}
		
		public void mark(int readLimit)
		{
			throw new RuntimeException("mark() is not implemented in class AudioSpriteInputStream.");
		}
		
		public void reset() throws IOException
		{
			throw new IOException("reset() is not implemented in class AudioSpriteInputStream.");
		}
		
		public boolean markSupported()
		{
			return false;
		}
	}
}
