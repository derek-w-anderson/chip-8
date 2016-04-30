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

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

/**
 * @author Derek Anderson
 */
public class Jip8 extends JFrame 
{
	private VM emulator;

	public Jip8()
	{
		super("JIP-8");

		// add menus
		setJMenuBar(createMenuBar());

		// add CHIP-8 display
		emulator = new VM();
		add(emulator.getScreen());

		// specify closing behavior
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				onExit();
			}
		});

		// update component L&F
		SwingUtilities.updateComponentTreeUI(this);

		// adjust window 
		pack();
		setResizable(false);
		Jip8.center(this);

		// display window
		setVisible(true);
	}

	private JMenuBar createMenuBar()
	{
		// force menus to stay above the display
		JPopupMenu.setDefaultLightWeightPopupEnabled(false);

		// create menu bar
		JMenuBar menuBar = new JMenuBar();
		JMenu fileMenu = new JMenu("File");
		JMenu helpMenu = new JMenu("Help");

		// create file menu options
		JMenuItem open = new JMenuItem("Load ROM...");
		open.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				onOpen();
			}
		});
		fileMenu.add(open);

		JMenuItem exit = new JMenuItem("Exit");
		exit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				onExit();
			}
		});
		fileMenu.add(exit);

		// create help menu options
		JMenuItem about = new JMenuItem("About");
		about.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				onAbout();
			}
		});
		helpMenu.add(about);

		// add components to GUI
		menuBar.add(fileMenu);
		menuBar.add(helpMenu);

		return menuBar;
	}

	private void onOpen()
	{
		FileDialog dialog = new FileDialog(this, "Load ROM", FileDialog.LOAD);
		dialog.setVisible(true);
		String dir = dialog.getDirectory();
		String file = dialog.getFile();

		// start emulation
		if (dir != null && file != null) {
			if (emulator.isRunning()) {
				emulator.stopEmulation();
				emulator.reset();
			}
			run(dir + file);
		}
	}

	private void onExit()
	{
		this.destroy();
		System.exit(0);
	}

	private void onAbout() {
		Dialog dialog = new AboutDialog(this);
		dialog.setVisible(true);
	}

	private class AboutDialog extends Dialog
	{
		public AboutDialog(Frame parent)
		{
			super(parent, true);
			setLayout(new FlowLayout());

			// add info text
			String text = new StringBuffer()
				.append("<html>")
				.append("<div style='text-align:center;'>")
				.append("<p>JIP-8 is a CHIP-8 emulator written in Java.</p>")
				.append("<p>Copyright (C) 2011 Derek Anderson.</p>")
				.append("<p>Use of this program's source code is subject to GNU GPL v3.</p>")
				.append("</div>")
				.append("</html>").toString();
			add(new JLabel(text));
			pack();

			// specify closing behavior
			addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent e) {
					setVisible(false);
				}
			});
			setTitle("About JIP-8");
			setResizable(false);

			// center on the root window
			Jip8.center(parent, this);
		}
	}

	/**
	 * @param filepath  path of ROM file
	 */
	private void run(String filepath)
	{
		try {
			// load program instructions from file
			byte[] program = FileLoader.load(filepath);

			if (program != null && program.length != 0) {

				// start emulating
				System.out.println("Starting emulation...");

				emulator.init(program);
				emulator.beginEmulation();

			} else {
				// error - empty byte array
				System.out.println("Error: no game data");
			}

		} catch (IOException ioe) {
			// error - can't load file
			ioe.printStackTrace();
			System.out.println("Error: cannot load file at path \"" + filepath + "\"");
		}
	}

	/**
	 * Destroys all components of the application.
	 */
	private void destroy()
	{
		System.out.println("Destroying application...");

		// stop emulating
		if (emulator != null && emulator.isRunning()) {
			emulator.stopEmulation();
		}
		// destroy the VM
		if (emulator != null) {
			emulator.destroy();
		}
		emulator = null;

		// signal for garbage collection
		System.runFinalization();
		System.gc();
	}

	// ==================================================================
	//  STATIC METHODS
	// ==================================================================
	/**
	 * Entry point for the application.
	 *
	 * @param args  command line arguments (not used)
	 */
	public static void main(String[] args)
	{
		System.out.println("----------------------------------------------");
		System.out.println(" JIP-8 is a CHIP-8 emulator written in Java   ");
		System.out.println(" Copyright (C) 2011 Derek Anderson            ");
		System.out.println(" Use of this program is subject to GNU GPL v3 ");
		System.out.println("----------------------------------------------");

		try {
			// use the OS's native GUI style
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			JFrame.setDefaultLookAndFeelDecorated(true);

		} catch (Exception e) {
			// can't use native style... fallback to Java's default L&F
		}

		// create a new thread for the app
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				new Jip8();
			}
		});
	}

	/**
	 * @param component object to be centered
	 */
	public static void center(Component component)
	{
		center(null, component);
	}

	/**
	 * @param parent    object for component to be centered on (if not null)
	 * @param component object to be centered
	 */
	public static void center(Component parent, Component component)
	{
		int centerX, centerY, xLoc, yLoc;

		if (parent == null) {
			// get center of primary screen
			Toolkit tk = Toolkit.getDefaultToolkit();
			Dimension screenSize = tk.getScreenSize();
			centerX = screenSize.width / 2;
			centerY = screenSize.height / 2;

		} else {
			// get center of parent component
			centerX = (parent.getX() + (parent.getWidth() / 2));
			centerY = (parent.getY() + (parent.getHeight() / 2));
		}
		// calculate new location
		xLoc = centerX - (component.getWidth() / 2);
		yLoc = centerY - (component.getHeight() / 2);
		component.setLocation(xLoc, yLoc);
	}
}
