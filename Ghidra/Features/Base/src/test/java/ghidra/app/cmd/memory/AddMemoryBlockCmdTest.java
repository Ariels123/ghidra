/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ghidra.app.cmd.memory;

import static org.junit.Assert.*;

import org.junit.*;

import generic.test.AbstractGenericTest;
import ghidra.framework.cmd.Command;
import ghidra.program.database.ProgramBuilder;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.mem.*;
import ghidra.util.exception.RollbackException;

/**
 * Test for the add memory block command.
 */
public class AddMemoryBlockCmdTest extends AbstractGenericTest {
	private Program notepad;
	private Program x08;
	private AddMemoryBlockCmd command;

	public AddMemoryBlockCmdTest() {
		super();
	}

	@Before
	public void setUp() throws Exception {

		ProgramBuilder notepadBuilder = new ProgramBuilder("notepad", ProgramBuilder._TOY);
		notepadBuilder.createMemory("test2", "0x1001010", 100);
		notepad = notepadBuilder.getProgram();

		ProgramBuilder x08Builder = new ProgramBuilder("x08", ProgramBuilder._8051);
		x08Builder.createMemory("test1", "0x0", 1);

		x08 = x08Builder.getProgram();
	}

	@Test
	public void testAddBlock() throws Exception {
		command = new AddMemoryBlockCmd(".test", "A Test", "new block", getNotepadAddr(0x100), 100,
			true, true, true, false, (byte) 0xa, MemoryBlockType.DEFAULT, null, true);
		assertTrue(applyCmd(notepad, command));
		MemoryBlock block = notepad.getMemory().getBlock(getNotepadAddr(0x100));
		assertNotNull(block);
		byte b = block.getByte(getNotepadAddr(0x100));
		assertEquals((byte) 0xa, b);

		// get the fragment for this block
		Listing listing = notepad.getListing();
		String[] treeNames = listing.getTreeNames();
		ProgramFragment f = listing.getFragment(treeNames[0], getNotepadAddr(0x100));
		assertNotNull(f);
		assertEquals(block.getName(), f.getName());
	}

	private boolean applyCmd(Program p, Command c) {
		int txId = p.startTransaction(c.getName());
		boolean commit = true;
		try {
			return c.applyTo(p);
		}
		catch (RollbackException e) {
			commit = false;
			throw e;
		}
		finally {
			p.endTransaction(txId, commit);
		}
	}

	@Test
	public void testOverlap() {
		command = new AddMemoryBlockCmd(".test", "A Test", "new block", getNotepadAddr(0x1001010),
			100, true, true, true, false, (byte) 0xa, MemoryBlockType.DEFAULT, null, true);
		try {
			applyCmd(notepad, command);
			Assert.fail("Should have gotten exception");
		}
		catch (RollbackException e) {
			// good
		}
		assertTrue(command.getStatusMsg().length() > 0);
	}

	@Test
	public void testAddBitBlock() {
		Address addr = getX08Addr(0x3000);
		command = new AddMemoryBlockCmd(".testBit", "A Test", "new block", addr, 100, true, true,
			true, false, (byte) 0, MemoryBlockType.BIT_MAPPED, getX08Addr(0), false);
		assertTrue(applyCmd(x08, command));

		MemoryBlock block = x08.getMemory().getBlock(addr);
		assertNotNull(block);
		assertEquals(getX08Addr(0), ((MappedMemoryBlock) block).getOverlayedMinAddress());
		assertEquals(MemoryBlockType.BIT_MAPPED, block.getType());
	}

	@Test
	public void testAddByteBlock() {
		Address addr = getX08Addr(0x3000);
		command = new AddMemoryBlockCmd(".testByte", "A Test", "new block", addr, 100, true, true,
			true, false, (byte) 0, MemoryBlockType.BYTE_MAPPED, getX08Addr(0), false);
		assertTrue(applyCmd(x08, command));

		MemoryBlock block = x08.getMemory().getBlock(addr);
		assertNotNull(block);
		assertEquals(getX08Addr(0), ((MappedMemoryBlock) block).getOverlayedMinAddress());
		assertEquals(MemoryBlockType.BYTE_MAPPED, block.getType());

	}

	@Test
	public void testAddOverlayBlock() throws Exception {
		Address addr = getX08Addr(0x3000);
		command = new AddMemoryBlockCmd(".overlay", "A Test", "new block", addr, 100, true, true,
			true, false, (byte) 0xa, MemoryBlockType.OVERLAY, getX08Addr(0), true);
		assertTrue(applyCmd(x08, command));

		MemoryBlock block = null;
		MemoryBlock[] blocks = x08.getMemory().getBlocks();
		for (MemoryBlock block2 : blocks) {
			if (block2.getName().equals(".overlay")) {
				block = block2;
				break;
			}
		}
		assertNotNull(block);
		assertEquals(MemoryBlockType.OVERLAY, block.getType());
		byte b = block.getByte(block.getStart().getNewAddress(0x3000));
		assertEquals((byte) 0xa, b);
	}

	private Address getX08Addr(int offset) {
		return x08.getMinAddress().getNewAddress(offset);
	}

	private Address getNotepadAddr(int offset) {
		return notepad.getMinAddress().getNewAddress(offset);
	}
}