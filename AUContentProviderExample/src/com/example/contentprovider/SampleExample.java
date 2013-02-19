package com.example.contentprovider;

import java.util.ArrayList;

import com.example.contentprovider.content.AppContract;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.BaseColumns;

import static com.appunite.contentprovider.ContentProviderHelper.*;

public class SampleExample {

	private final ContentResolver mCr;
	private int mBookIdRef;

	public SampleExample(ContentResolver cr) {
		mCr = cr;
	}

	public void fillExampleData() {
		clearDatabaseAndAddBookInOneTransaction();
		addLordOfTheRingsOneByOne();
		remoteServerParseExample();

		// This can be called as many times as you want. Because
		// when inserting with "guid" field AUContentProvider ensures
		// that this field is not in database - if it is. AUContentProvider
		// only update this value.
		// After full sync not updated values are deleted - your data will be
		// consistent.
		remoteServerParseExample();

		// Normally you should use Loaders but for this simple propose we use
		// simple queries

		// You can look in logcat when you in debug mode to see what sql queries
		// was generated
		exampleSelects();
	}

	private void exampleSelects() {
		// If you want you can add BaseColumns._ID field if you want to use
		// ListView Adapter - it require _id field

		// You can ask for remote field if you have connection 1:N in DBHelper

		// You can even sort by remote field - remember that you have to have
		// this remote field in projection
		Cursor q1 = mCr.query(AppContract.Author.CONTENT_URI, new String[] {
				BaseColumns._ID, AppContract.Author.AUTHOR_ID,
				AppContract.Author.NAME, AppContract.Book.NAME }, null, null,
				AppContract.Book.NAME + " ASC");
		q1.close();

		// You can have selection by remote field - remember that you have to
		// have this remote field in projection
		Cursor q2 = mCr.query(AppContract.Author.CONTENT_URI, new String[] {
				BaseColumns._ID, AppContract.Author.AUTHOR_ID,
				AppContract.Author.NAME, AppContract.Book.NAME },
				AppContract.Book.NAME + " != ?",
				new String[] { "Some author 2" }, null);
		q2.close();

		// When you want to append projection for speciffic query you can use
		// joinProjections method
		String[] PROJECTION = new String[] { BaseColumns._ID,
				AppContract.Author.AUTHOR_ID, AppContract.Author.NAME };
		Cursor q3 = mCr.query(AppContract.Author.CONTENT_URI,
				joinProjections(PROJECTION, AppContract.Book.NAME),
				AppContract.Book.NAME + " != ?",
				new String[] { "Some author 2" }, null);
		q3.close();

		// You can use fake sql fields too
		Cursor q4 = mCr.query(AppContract.Book.CONTENT_URI, new String[] {
				AppContract.Book.BOOK_ID, AppContract.Book.AUTHORS_COUNT },
				null, null, null);
		q4.close();
	}

	private void remoteServerParseExample() {
		long syncToken = System.currentTimeMillis();
		ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

		// Add book with authors
		addBook(ops, "serverGuid1", "Some book 1", syncToken);
		addAuthorToBook(ops, "serverGuid1", "Some author 1", syncToken);
		addAuthorToBook(ops, "serverGuid2", "Some author 2", syncToken);

		// Add second book with authors
		addBook(ops, "serverGuid2", "Some book 2", syncToken);
		addAuthorToBook(ops, "serverGuid3", "Some author 3", syncToken);
		addAuthorToBook(ops, "serverGuid4", "Some author 4", syncToken);

		// delete old (not updated) elements in database
		ops.add(ContentProviderOperation
				.newDelete(AppContract.Book.CONTENT_URI)
				.withSelection(AppContract.Book.SYNC_TOKEN + " != ?",
						new String[] { String.valueOf(syncToken) }).build());
		ops.add(ContentProviderOperation
				.newDelete(AppContract.Book.CONTENT_URI)
				.withSelection(AppContract.Book.SYNC_TOKEN + " != ?",
						new String[] { String.valueOf(syncToken) }).build());
		try {
			// apply all operations in one transaction
			mCr.applyBatch(AppContract.AUTHORITY, ops);
		} catch (RemoteException e) {
			throw new RuntimeException(
					"Something went wrong with connection to content resolver",
					e);
		} catch (OperationApplicationException e) {
			throw new RuntimeException("Something is wrong with your sql", e);
		}
	}

	private void addAuthorToBook(ArrayList<ContentProviderOperation> ops,
			String guid, String name, long syncToken) {
		ops.add(ContentProviderOperation
				.newInsert(AppContract.Author.CONTENT_URI)
				.withValue(AppContract.Author.GUID, guid)
				.withValue(AppContract.Author.NAME, name)
				.withValueBackReference(AppContract.Author.BOOK_ID, mBookIdRef)
				.withValue(AppContract.Author.SYNC_TOKEN, syncToken).build());
	}

	private void addBook(ArrayList<ContentProviderOperation> ops, String guid,
			String name, long syncToken) {
		mBookIdRef = ops.size();
		ops.add(ContentProviderOperation
				.newInsert(AppContract.Book.CONTENT_URI)
				.withValue(AppContract.Book.GUID, guid)
				.withValue(AppContract.Book.NAME, name)
				.withValue(AppContract.Book.SYNC_TOKEN, syncToken).build());
	}

	private void clearDatabaseAndAddBookInOneTransaction() {
		ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

		// delete all items from table
		ops.add(ContentProviderOperation.newDelete(
				AppContract.Author.CONTENT_URI).build());
		ops.add(ContentProviderOperation
				.newDelete(AppContract.Book.CONTENT_URI).build());

		// save reference number of book insert operation
		int bookIdRef = ops.size();
		ops.add(ContentProviderOperation
				.newInsert(AppContract.Book.CONTENT_URI)
				.withValue(AppContract.Book.NAME,
						"The Hitchhiker's Guide to the Galaxy").build());
		// add author to book using back reference
		ops.add(ContentProviderOperation
				.newInsert(AppContract.Author.CONTENT_URI)
				.withValue(AppContract.Author.NAME, "Douglas Adams")
				.withValueBackReference(AppContract.Author.BOOK_ID, bookIdRef)
				.build());
		try {
			// apply all operations in one transaction
			mCr.applyBatch(AppContract.AUTHORITY, ops);
		} catch (RemoteException e) {
			throw new RuntimeException(
					"Something went wrong with connection to content resolver",
					e);
		} catch (OperationApplicationException e) {
			throw new RuntimeException("Something is wrong with your sql", e);
		}
	}

	private void addLordOfTheRingsOneByOne() {
		ContentValues values = new ContentValues();
		values.put(AppContract.Book.NAME, "The Lord of the Rings");
		Uri lordOfTheRingsUri = mCr
				.insert(AppContract.Book.CONTENT_URI, values);
		long lordOfTheRingdId = Long.valueOf(lordOfTheRingsUri
				.getLastPathSegment());
		ContentValues values2 = new ContentValues();
		values2.put(AppContract.Author.NAME, "J. R. R. Tolkien");
		values2.put(AppContract.Author.BOOK_ID, lordOfTheRingdId);
		mCr.insert(AppContract.Author.CONTENT_URI, values2);
	}

}
