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

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Random;

/**
 * Emulates a CHIP-8 virtual machine.
 *
 * @author Derek Anderson
 */
public class VM implements Runnable
{
	// CHIP-8 provides 4KB (4096 bytes) of total memory. The first 512 bytes
	// (i.e. addresses 0x000 -> 0x1FF) are reserved for the interpreter, so the
	// effective address range for programs is 0x200 -> 0xFFF.
	private byte[] mem;
	private static final int MEM_START = 0x200;
	private static final int MEM_END   = 0xFFF;

	// General-purpose registers include a 16-bit address register (I) and 16
	// 8-bit data registers (V0 -> VF).
	private short  I;
	private byte[] V;

	// System registers include a 16-bit program counter (PC) and an 8-bit stack
	// pointer (SP).
	private short PC;
	private byte  SP;

	// The call stack stores up to 16 return addresses for subroutine calls.
	private short[] stack;

	// CHIP-8 provides two special-purpose timers. The delay timer is active
	// whenever the delay timer register (DT) is non-zero and subtracts 1 from
	// the value stored in DT at a rate of 60Hz (60 cycles/sec). The sound timer
	// also decrements at a rate of 60Hz, and sounds CHIP-8's buzzer whenever
	// the sound timer register (ST) is non-zero.
	private byte DT;
	private byte ST;

	// CHIP-8 programs draw sprite graphics to a 64x32-pixel monochrome display.
	// Sprites may be up to 15 bytes long, so their max size is 8x15 pixels.
	private Display screen;

	// Programs may refer to a sprite set representing any hex digit (0 -> F).
	// These sprites are 5 bytes long (8x5 pixels) and are stored in the area of
	// memory reserved for the interpreter (addresses 0x000 -> 0x0AF).
	private short[] hexSprite;

	// CHIP-8 keyboard input
	// TODO: get some keyboard listeners

	// Misc vars
	Thread thisThread;
	boolean stopped;

	// Interpreter vars
	byte collision;
	byte x, y, n;
	byte kk, rand;
	short opcode, nnn;
	Random randgen;
	Timer clock;

	/**
	 * Constructs a VM.
	 */
	public VM()
	{
		// create memory
		mem = new byte[MEM_END];

		// copy hex sprites into memory
		hexSprite = fillHexSpriteTable();

		// create call stack
		stack = new short[16];

		// create data registers
		V = new byte[16];

		// create screen
		screen = new Display(64, 32);

		// create random number generator
		randgen = new Random();

		// create system clock timer (runs at 60 Hz)
		clock = new Timer(1000 / 60, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				clockCycle();
			}
		});
	}

	/**
	 * Initializes the VM. 
	 *
	 * @param opcodes   byte array of CHIP-8 instructions
	 */
	public void init(byte[] opcodes)
	{
		// copy program instructions into memory
		System.arraycopy(opcodes, 0, this.mem, MEM_START, opcodes.length);

		// set program counter
		PC = MEM_START;
	}

	/**
	 * Resets the VM.
	 */
	public void reset()
	{
		int i;

		// clear program instructions
		for (i = MEM_START; i < MEM_END; i++) {
			mem[i] = 0x00;
		}
		// clear call stack
		for (i = 0; i < 16; i++) {
			stack[i] = 0x0000;
		}
		SP = 0;

		// clear registers
		I = 0x0000;
		for (i = 0; i < 16; i++) {
			V[i] = 0x00;
		}
		// reset timers
		DT = 0;
		ST = 0;

		// clear screen
		screen.clear();
	}

	/**
	 * Creates a new VM thread to start executing instructions.
	 */
	public synchronized void beginEmulation()
	{
		// kill existing thread 
		if (thisThread != null && thisThread.isAlive()) {
			stopEmulation();
		}
		// create a new thread
		thisThread = new Thread(this);
		thisThread.start();
		thisThread.setPriority(Thread.MIN_PRIORITY);
	}

	/**
	 * Fulfills the Runnable interface contract, but VM instances should only be
	 * invoked by calling init() followed by beginEmulation().
	 */
	public void run()
	{
		clock.start();
		stopped = false;
		emulate();
	}

	/**
	 * Decrements timers and refreshes the screen. Runs at 60 Hz.
	 */
	private void clockCycle()
	{
		if (DT > 0) { DT--; }
		if (ST > 0) { ST--; }
		screen.repaint();
	}

	/**
	 * Fetches, decodes, and executes CHIP-8 instructions. Normal execution
	 * speed is too fast for some CHIP-8 programs to run properly, so we limit
	 * the number of instructions that get executed every second.
	 */
	private void emulate()
	{
		long delta, start = System.nanoTime();

		while (!stopped) {
			delta = System.nanoTime() - start;

			if (delta % 1000000 < 2500) {
				fetch();
				decode();
				execute();
			}
		}
	}

	/**
	 * Fetches the next instruction to be executed. Every CHIP-8 instruction is
	 * two bytes long, so each byte must be retrieved from memory and then
	 * combined into one 16-bit opcode.
	 */
	private void fetch()
	{
		opcode = (short) ((mem[PC] << 8) | (mem[PC+1] & 0xFF));
		PC += 2;
	}

	/**
	 * Decodes the current instruction.
	 */
	private void decode()
	{
		x   = (byte) ((opcode & 0x0F00) >> 8);  // 4-bit value
		y   = (byte) ((opcode & 0x00F0) >> 4);  // 4-bit value
		n   = (byte)  (opcode & 0x000F);        // 4-bit value
		kk  = (byte)  (opcode & 0x00FF);        // 8-bit value
		nnn = (short) (opcode & 0x0FFF);        // 12-bit address
	}

	/**
	 * Executes the current instruction.
	 */
	private void execute()
	{
		System.out.printf("%03X: [%04X] ", PC-2, opcode);

		// jump to correct execution path
		switch (opcode & 0xF000) {
			case 0x0000:

				switch (opcode & 0x00FF) {
					case 0x00E0: // CLS: clear the display
						System.out.printf("CLS");
						screen.clear();
						break;

					case 0x00EE: // RET: return from subroutine
						System.out.printf("RET");
						PC = stack[SP];
						SP--;
						break;

					default:
						System.out.printf("Illegal opcode!");
				}
				break;

			case 0x1000: // JP addr: jump to address nnn
				System.out.printf("JP %03X", nnn);
				PC = nnn;
				break;

			case 0x2000: // CALL addr: call subroutine at nnn
				System.out.printf("CALL %03X", nnn);
				SP++;
				stack[SP] = PC;
				PC = nnn;
				break;

			case 0x3000: // SE Vx, byte: skip next op if Vx = kk
				System.out.printf("SE V%X, %02X", x, kk);
				if (V[x] == kk) { PC += 2; }
				break;

			case 0x4000: // SNE Vx, byte: skip next op if Vx != kk.
				System.out.printf("SNE V%X, V%X", x, y);
				if (V[x] != kk) { PC += 2; }
				break;

			case 0x5000: // SE Vx, Vy: skip next op if Vx = Vy
				System.out.printf("SE V%X, V%X", x, y);
				if (V[x] == V[y]) {	PC += 2; }
				break;

			case 0x6000: // LD Vx, byte: set Vx = kk
				System.out.printf("LD V%X, %02X", x, kk);
				V[x] = kk;
				break;

			case 0x7000: // ADD Vx, byte: set Vx = Vx + kk
				System.out.printf("ADD V%X, %02X", x, kk);
				V[x] += kk;
				break;

			case 0x8000:
				math(opcode);
				break;

			case 0x9000: // SNE Vx, Vy: skip next instruction if Vx != Vy
				System.out.printf("SNE V%X, V%X", x, y);
				if (V[x] != V[y]) { PC += 2; }
				break;

			case 0xA000: // LD I, addr: set I = nnn
				System.out.printf("LD I, %03X", nnn);
				I = nnn;
				break;

			case 0xB000: // JP V0, addr: jump to address nnn + V0
				System.out.printf("JP V0, %03X", nnn);
				PC = (short) (nnn + V[0]);
				break;

			case 0xC000: // RND Vx, byte: set Vx = random byte AND kk
				System.out.printf("RND V%X, %02X", x, kk);
				rand = (byte) randgen.nextInt(256);
				V[x] = (byte) (rand & kk);
				break;

			case 0xD000: // DRW Vx, Vy, n: draw sprite, set VF = collision
				System.out.printf("DRW V%X, V%X, %d ", x, y, n);
				collision = screen.draw(mem, n, I, V[x], V[y]);
				V[0xF] = collision;
				break;

			case 0xE000:

				switch (opcode & 0xF0FF) {
					case 0xE09E: // SKP Vx: skip instruction if key Vx is pressed
						System.out.printf("SKP V%X", x);
						// TODO: check the keyboard
						// TODO: if the key corresponding to the value of Vx is down, increment PC by 2
						break;

					case 0xE0A1: // SKNP Vx: skip instruction if key Vx is NOT pressed
						System.out.printf("SKNP V%X", x);
						// TODO: check the keyboard
						// TODO: if the key corresponding to the value of Vx is up, increment PC by 2
						PC += 2; // keys assumed up for now
						break;

					default:
						System.out.printf("Illegal opcode!");
				}
				break;

			case 0xF000:

				switch (opcode & 0xF0FF) {
					case 0xF007: // LD Vx, DT: set Vx = delay timer value.
						System.out.printf("LD V%X, DT", x);
						V[x] = DT;
						break;

					case 0xF00A: // LD Vx, K: wait for key press, store key value in Vx
						System.out.printf("LD V%X, K", x);
						// TODO: do something here
						break;

					case 0xF015: // LD DT, Vx: set delay timer = Vx
						System.out.printf("LD DT, V%X", x);
						DT = V[x];
						break;

					case 0xF018: // LD ST, Vx: set sound timer = Vx
						System.out.printf("LD ST, V%X", x);
						ST = V[x];
						break;

					case 0xF01E: // ADD I, Vx: set I = I + Vx
						System.out.printf("ADD I, V%X", x);
						I += V[x];
						V[0xF] = flag(I + V[x] > 0xFFF); // NOTE: undocumented behavior
						break;

					case 0xF029: // LD F, Vx: set I = location of sprite for hex digit Vx
						System.out.printf("LD F, V%X", x);
						I = hexSprite[V[x]];
						break;

					case 0xF033: // LD B, Vx: store BCD representation of Vx in memory at locations I, I+1, and I+2
						System.out.printf("LD B, V%X", x);
						mem[I]   = (byte)  (V[x] / 100);        // hundreds digit
						mem[I+1] = (byte) ((V[x] / 10) % 10);   // tens digit
						mem[I+2] = (byte) ((V[x] % 100) % 10);  // ones digit
						break;

					case 0xF055: // LD [I], Vx: store registers V0 -> Vx in memory starting at location I
						System.out.printf("LD [I], V%X", x);
						System.arraycopy(V, 0, mem, I, x+1);
						I += x + 1; // NOTE: undocumented behavior
						break;

					case 0xF065: // LD Vx, [I]: load registers V0 -> Vx from memory starting at location I
						System.out.printf("LD V%X, [I]", x);
						System.arraycopy(mem, I, V, 0, x+1);
						I += x + 1; // NOTE: undocumented behavior
						break;

					default:
						System.out.printf("Illegal opcode!");
				}
				break;

			default:
				System.out.printf("Illegal opcode!");

		} // end of instruction switch block
		System.out.printf("\n");
		System.out.printf("\tI:%X, ST:%X, DT:%X\n", I, ST, DT);
		System.out.printf("\t0:%02X, 1:%02X, 2:%02X, 3:%02X, 4:%02X, 5:%02X, 6:%02X, 7:%02X \n", V[0], V[1], V[2], V[3], V[4], V[5], V[6], V[7]);
		System.out.printf("\t8:%02X, 9:%02X, A:%02X, B:%02X, C:%02X, D:%02X, E:%02X, F:%02X \n", V[8], V[9], V[0xA], V[0xB], V[0xC], V[0xD], V[0xE], V[0xF]);
	}

	/**
	 * Returns a numerical representation of the given boolean expression.
	 * 
	 * @param condition expression to be evaluated
	 * @return          1 if condition is true, else 0
	 */
	private byte flag(boolean condition)
	{
		return (byte) (condition ? 1 : 0);
	}

	/**
	 * Executes a mathematical CHIP-8 instruction.
	 *
	 * @param opcode    instruction to be executed
	 */
	private void math(short opcode)
	{
		switch (opcode & 0xF00F) {
			case 0x8000: // LD Vx, Vy: set Vx = Vy
				System.out.printf("LD V%X, V%X", x, y);
				V[x] = V[y];
				break;

			case 0x8001: // OR Vx, Vy: set Vx = Vx | Vy
				System.out.printf("OR V%X, V%X", x, y);
				V[x] |= V[y];
				break;

			case 0x8002: // AND Vx, Vy: set Vx = Vx & Vy
				System.out.printf("AND V%X, V%X", x, y);
				V[x] &= V[y];
				break;

			case 0x8003: // XOR Vx, Vy: set Vx = Vx ^ Vy
				System.out.printf("XOR V%X, V%X", x, y);
				V[x] ^= V[y];
				break;

			case 0x8004: // ADD Vx, Vy: set Vx = Vx + Vy, set VF = carry
				System.out.printf("ADD V%X, V%X", x, y);
				V[0xF] = flag((V[x] + V[y]) > 255);
				V[x] += V[y];
				break;

			case 0x8005: // SUB Vx, Vy: set Vx = Vx - Vy, set VF = NOT borrow
				System.out.printf("SUB V%X, V%X", x, y);
				V[0xF] = flag(V[x] > V[y]);
				V[x] -= V[y];
				break;

			case 0x8006: // SHR Vx: set Vx = Vx >> 1, set VF = (LSB of Vx == 1)
				System.out.printf("SHR V%X", x);
				V[0xF] = flag((V[x] & 1) == 1);
				V[x] = (byte) ((V[x] & 0xFF) >>> 1); // note: unsigned right shift operator
				break;

			case 0x8007: // SUBN Vx, Vy: set Vx = Vy - Vx, set VF = NOT borrow
				System.out.printf("SUBN V%X, V%X", x, y);
				V[0xF] = flag(V[y] >= V[x]);
				V[x] = (byte) ((V[y] - V[x]) & 0xFF);
				break;

			case 0x800E: // SHL Vx: set Vx = Vx << 1, set VF = (MSB of Vx == 1)
				System.out.printf("SHL V%X", x);
				V[0xF] = flag(((V[x] & 0xFF) >>> 7) == 1); // note: unsigned right shift operator
				V[x] <<= 1;
				break;

			default:
				System.out.printf("Illegal opcode! \n");
		}
	}

	/**
	 * Nullifies every object referenced by the VM.
	 */
	public void destroy()
	{
		mem = null;
		V = null;
		stack = null;
		screen = null;
		hexSprite = null;
		randgen = null;
	}

	/**
	 * Copies sprites into reserved address space.
	 *
	 * @return  array of hex sprite addresses
	 */
	private short[] fillHexSpriteTable()
	{
		short[] addr = new short[16];

		addr[0] = 0x000;
		mem[0x000] = (byte) 0xF0; // 1111    ****
		mem[0x001] = (byte) 0x90; // 1001    *  *
		mem[0x002] = (byte) 0x90; // 1001 -> *  *
		mem[0x003] = (byte) 0x90; // 1001    *  *
		mem[0x004] = (byte) 0xF0; // 1111    ****

		addr[1] = 0x005;
		mem[0x005] = (byte) 0x20; // 0010      *
		mem[0x006] = (byte) 0x60; // 0110     **
		mem[0x007] = (byte) 0x20; // 0010 ->   *
		mem[0x008] = (byte) 0x20; // 0010      *
		mem[0x009] = (byte) 0x70; // 0111     ***

		addr[2] = 0x00A;
		mem[0x00A] = (byte) 0xF0; // 1111    ****
		mem[0x00B] = (byte) 0x10; // 0001       *
		mem[0x00C] = (byte) 0xF0; // 1111 -> ****
		mem[0x00D] = (byte) 0x80; // 1000    *
		mem[0x00E] = (byte) 0xF0; // 1111    ****

		addr[3] = 0x00F;
		mem[0x00F] = (byte) 0xF0; // 1111    ****
		mem[0x010] = (byte) 0x10; // 0001       *
		mem[0x011] = (byte) 0xF0; // 1111 -> ****
		mem[0x012] = (byte) 0x10; // 0001       *
		mem[0x013] = (byte) 0xF0; // 1111    ****

		addr[4] = 0x014;
		mem[0x014] = (byte) 0x90; // 1001    *  *
		mem[0x015] = (byte) 0x90; // 1001    *  *
		mem[0x016] = (byte) 0xF0; // 1111 -> ****
		mem[0x017] = (byte) 0x10; // 0001       *
		mem[0x018] = (byte) 0x10; // 0001       *

		addr[5] = 0x019;
		mem[0x019] = (byte) 0xF0; // 1111    ****
		mem[0x01A] = (byte) 0x80; // 1000    *
		mem[0x01B] = (byte) 0xF0; // 1111 -> ****
		mem[0x01C] = (byte) 0x10; // 0001       *
		mem[0x01D] = (byte) 0xF0; // 1111    ****

		addr[6] = 0x01E;
		mem[0x01E] = (byte) 0xF0; // 1111    ****
		mem[0x01F] = (byte) 0x80; // 1000    *
		mem[0x020] = (byte) 0xF0; // 1111 -> ****
		mem[0x021] = (byte) 0x90; // 1001    *  *
		mem[0x022] = (byte) 0xF0; // 1111    ****

		addr[7] = 0x023;
		mem[0x023] = (byte) 0xF0; // 1111    ****
		mem[0x024] = (byte) 0x10; // 0001       *
		mem[0x025] = (byte) 0x20; // 0010 ->   *
		mem[0x026] = (byte) 0x40; // 0100     *
		mem[0x027] = (byte) 0x40; // 0100     *

		addr[8] = 0x028;
		mem[0x028] = (byte) 0xF0; // 1111    ****
		mem[0x029] = (byte) 0x90; // 1001    *  *
		mem[0x02A] = (byte) 0xF0; // 1111 -> ****
		mem[0x02B] = (byte) 0x90; // 1001    *  *
		mem[0x02C] = (byte) 0xF0; // 1111    ****

		addr[9] = 0x02D;
		mem[0x02D] = (byte) 0xF0; // 1111    ****
		mem[0x02E] = (byte) 0x90; // 1001    *  *
		mem[0x02F] = (byte) 0xF0; // 1111 -> ****
		mem[0x030] = (byte) 0x10; // 0001       *
		mem[0x031] = (byte) 0xF0; // 1111    ****

		addr[0xA] = 0x032;
		mem[0x032] = (byte) 0xF0; // 1111    ****
		mem[0x033] = (byte) 0x90; // 1001    *  *
		mem[0x034] = (byte) 0xF0; // 1111 -> ****
		mem[0x035] = (byte) 0x90; // 1001    *  *
		mem[0x036] = (byte) 0x90; // 1001    *  *

		addr[0xB] = 0x037;
		mem[0x037] = (byte) 0xE0; // 1110    ***
		mem[0x038] = (byte) 0x90; // 1001    *  *
		mem[0x039] = (byte) 0xE0; // 1110 -> ***
		mem[0x03A] = (byte) 0x90; // 1001    *  *
		mem[0x03B] = (byte) 0xE0; // 1110    ***

		addr[0xC] = 0x03C;
		mem[0x03C] = (byte) 0xF0; // 1111    ****
		mem[0x03D] = (byte) 0x80; // 1000    *
		mem[0x03E] = (byte) 0x80; // 1000 -> *
		mem[0x03F] = (byte) 0x80; // 1000    *
		mem[0x040] = (byte) 0xF0; // 1111    ****

		addr[0xD] = 0x041;
		mem[0x041] = (byte) 0xE0; // 1110    ***
		mem[0x042] = (byte) 0x90; // 1001    *  *
		mem[0x043] = (byte) 0x90; // 1001 -> *  *
		mem[0x044] = (byte) 0x90; // 1001    *  *
		mem[0x045] = (byte) 0xE0; // 1110    ***

		addr[0xE] = 0x046;
		mem[0x046] = (byte) 0xF0; // 1111    ****
		mem[0x047] = (byte) 0x80; // 1000    *
		mem[0x048] = (byte) 0xF0; // 1111 -> ****
		mem[0x049] = (byte) 0x80; // 1000    *
		mem[0x04A] = (byte) 0xF0; // 1111    ****

		addr[0xF] = 0x04B;
		mem[0x04B] = (byte) 0xF0; // 1111    ****
		mem[0x04C] = (byte) 0x80; // 1000    *
		mem[0x04D] = (byte) 0xF0; // 1111 -> ****
		mem[0x04E] = (byte) 0x80; // 1000    *
		mem[0x04F] = (byte) 0x80; // 1000    *

		return addr;
	}

	/**
	 * Stops executing instructions and kills this thread.
	 */
	public synchronized void stopEmulation()
	{
		if (thisThread != null && thisThread.isAlive()) {
			System.out.println("Stopping VM...");

			try {
				clock.stop();       // stop system clock
				stopped = true;     // stop fetch/decode/execute cycle
				thisThread.join();  // kill this thread

			} catch (InterruptedException iex) {
				System.out.println("Unable to stop VM!");
				iex.printStackTrace();
			}
		} else {
			System.out.println("VM is already stopped");
		}
	}

	/**
	 * Returns the status of this thread.
	 *
	 * @return  true if thread is running, else false
	 */
	public boolean isRunning()
	{
		return (thisThread != null && thisThread.isAlive());
	}

	/**
	 * Returns the VM's monochromatic display.
	 *
	 * @return  reference to CHIP-8 screen
	 */
	public Display getScreen()
	{
		return screen;
	}
}