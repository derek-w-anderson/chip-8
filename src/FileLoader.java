/*
 * Jip-8 is a CHIP-8 emulator written in Java.
 *
 * Copyright (C) 2011 Derek Anderson.
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for details.
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class FileLoader
{
	/**
	 * Protected constructor for static class.
	 */
	protected FileLoader() { }

	public static byte[] load(String filepath) throws IOException
	{
		File file = new File(filepath);
		InputStream is = new FileInputStream(file);

		// get file size
		long length = file.length();

		if (length > Integer.MAX_VALUE) {
			// file is too large
			return null;
		}

		// create the byte array to hold the data
		byte[] bytes = new byte[(int)length];

		// read in the bytes
		int offset = 0;
		int numRead = 0;
		while ((offset < bytes.length) &&
		       (numRead=is.read(bytes, offset, bytes.length-offset)) >= 0) {
			offset += numRead;
		}

		// ensure all bytes have been read
		if (offset < bytes.length) {
			throw new IOException("Could not completely read file \"" + file.getName() + "\"");
		}

		// close the input stream and return bytes
		is.close();
		return bytes;
	}
}
