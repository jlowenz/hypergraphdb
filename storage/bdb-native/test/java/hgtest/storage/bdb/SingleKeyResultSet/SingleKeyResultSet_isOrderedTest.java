package hgtest.storage.bdb.SingleKeyResultSet;

import com.sleepycat.db.Cursor;
import com.sleepycat.db.DatabaseEntry;
import hgtest.storage.bdb.ResultSetTestBasis;
import org.easymock.EasyMock;
import org.hypergraphdb.storage.ByteArrayConverter;
import org.hypergraphdb.storage.bdb.BDBTxCursor;
import org.hypergraphdb.storage.bdb.SingleKeyResultSet;
import org.powermock.api.easymock.PowerMock;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;

/**
 * @author Yuriy Sechko
 */
public class SingleKeyResultSet_isOrderedTest extends ResultSetTestBasis
{
	@Test
	public void test() throws Exception
	{
		final Cursor realCursor = database.openCursor(
				transactionForTheEnvironment, null);
		realCursor.put(new DatabaseEntry(new byte[] { 1, 2, 3, 4 }),
				new DatabaseEntry(new byte[] { 1, 2, 3, 4 }));
		final BDBTxCursor fakeCursor = PowerMock
				.createStrictMock(BDBTxCursor.class);
		EasyMock.expect(fakeCursor.cursor()).andReturn(realCursor).times(2);
		PowerMock.replayAll();
		final ByteArrayConverter<Integer> converter = new hgtest.TestUtils.ByteArrayConverterForInteger();
		final SingleKeyResultSet<Integer> resultSet = new SingleKeyResultSet(
				fakeCursor, null, converter);

		final boolean isOrdered = resultSet.isOrdered();

		assertFalse(isOrdered);
		realCursor.close();
	}
}
