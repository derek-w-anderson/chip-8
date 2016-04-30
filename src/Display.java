/*
 * JIP-8 is a CHIP-8 emulator written in Java.
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

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

/**
 * A monochromatic display for CHIP-8 programs.
 *
 * @author Derek Anderson
 */
public class Display extends JPanel
{
	public static final int PIXEL_SIZE = 8;

	private static final int ON  = 1;
	private static final int OFF = 0;

	private int rows, cols;
	private int[][] pixel;

	private int width, height;

	private BufferedImage img;
	private Graphics gfx;

	public Display(int cols, int rows)
	{
		this.rows = rows;
		this.cols = cols;
		pixel = new int[cols][rows];

		width = cols * PIXEL_SIZE;
		height = rows * PIXEL_SIZE;

		// set background color
		setBackground(Color.BLACK);

		// create screen buffer
		img = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
		gfx = img.getGraphics();

		// set the preferredSize to make pack() work 
		setPreferredSize(new Dimension(width, height));
	}

	@Override
	public void paint(Graphics g)
	{
		g.drawImage(img, 0, 0, this);
	}

	public void update(Graphics g)
	{
		paint(g);
	}

	/**
	 * Clears everything from the screen.
	 */
	public void clear()
	{
		// clear pixels
		for (int x = 0; x < cols; x++) {
			for (int y = 0; y < rows; y++) {
				pixel[x][y] = OFF;
			}
		}
		// clear buffer
		gfx.clearRect(0, 0, width, height);
	}

	/**
	 * Draws an n-byte sprite from memory location I at (Vx, Vy). Each bit in
	 * a sprite byte represents an individual pixel on the screen.
	 *
	 * @param mem   program data
	 * @param n     size of sprite (in bytes)
	 * @param I     address of sprite's first byte
	 * @param Vx    x-coordinate of sprite
	 * @param Vy    y-coordinate of sprite
	 *
	 * @return      1 if collision occurred, else 0
	 */
	public byte draw(byte[] mem, byte n, short I, byte Vx, byte Vy)
	{
		int bit, bitpos, bitmask;
		int xLoc = -1, yLoc = -1;
		byte sbyte, collision = 0;

		try {

			// loop through sprite bytes
			for (int y = 0; y < n; y++) {
				sbyte = mem[I+y];

				 // get y-coordinate of pixel (wrap if necessary)
				yLoc = (Vy + y < rows) ? (Vy + y) : ((Vy + y) - rows);

				// loop through bits in sprite byte
				for (int x = 0; x <= 7; x++) {
					bitpos = 7 - x;
					bitmask = (1 << bitpos);
					bit = ((sbyte & bitmask) == 0) ? OFF : ON;

					 // get x-coordinate of pixel (wrap if necessary)
					xLoc = (Vx + x < cols) ? (Vx + x) : ((Vx + x) - cols);

					// check for collision (i.e. pixel being erased)
					if (pixel[xLoc][yLoc] == ON && (pixel[xLoc][yLoc] ^ bit) == OFF) {
						collision = 1;
					}
					// XOR pixel with sprite bit
					pixel[xLoc][yLoc] ^= bit;

					// draw pixel to buffer
					gfx.setColor(pixel[xLoc][yLoc] == ON ? Color.WHITE : Color.BLACK);
					gfx.fillRect(xLoc * PIXEL_SIZE, yLoc * PIXEL_SIZE, PIXEL_SIZE, PIXEL_SIZE);
				}
			}

		} catch (Exception ex) {
			System.out.print("\t\t\t\t\t\t\t[drawing error at (" + xLoc + ", " + yLoc + ")]");
		}
		return collision; 
	}
}
