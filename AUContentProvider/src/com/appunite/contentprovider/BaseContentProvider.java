package com.appunite.contentprovider;

import java.util.ArrayList;
import java.util.List;

import com.appunite.contentprovider.ContractFullDesc.SelectionVars;

import android.annotation.TargetApi;
import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

public abstract class BaseContentProvider extends ContentProvider {

	private static final int MAX_OPERATIONS_PER_YIELD_POINT = 500;
	private static final int MIN_OPERATIONS_PER_YIELD_POINT = 250;
	private static final int FORCE_OPERATIONS_PER_YIELD_POINT = 400;
	private static final long SLEEP_AFTER_YIELD_DELAY = 4000;
	private static final String TAG = "BaseContentProvider";

	private SQLiteOpenHelper mDatabase;
	private SQLiteDatabase mDb;

	public BaseContentProvider() {
		super();
	}

	protected abstract ContractFullDesc getFullDesc();

	protected abstract SQLiteOpenHelper createSQLiteOpenHelper(Context context);

	@Override
	public boolean onCreate() {
		mDatabase = createSQLiteOpenHelper(getContext());
		return true;
	}

	private SQLiteDatabase getDb() {
		if (mDb != null) {
			return mDb;
		}
		mDb = mDatabase.getWritableDatabase();
		return mDb;
	}

	@Override
	public String getType(Uri uri) {
		return getFullDesc().getTypeURI(uri);
	}

	@Override
	public ContentProviderResult[] applyBatch(
			ArrayList<ContentProviderOperation> operations)
			throws OperationApplicationException {
		int ypCount = 0;
		int opCount = 0;
		SQLiteDatabase db = getDb();
		db.beginTransaction();
		try {
			final int numOperations = operations.size();
			final ContentProviderResult[] results = new ContentProviderResult[numOperations];
			for (int i = 0; i < numOperations; i++) {
				if (++opCount >= MAX_OPERATIONS_PER_YIELD_POINT) {
					throw new OperationApplicationException(
							"Too many content provider operations between yield points. "
									+ "The maximum number of operations per yield point is "
									+ MAX_OPERATIONS_PER_YIELD_POINT, ypCount);
				}
				final ContentProviderOperation operation = operations.get(i);
				if (opCount > MIN_OPERATIONS_PER_YIELD_POINT
						&& operation.isYieldAllowed()
						|| opCount > FORCE_OPERATIONS_PER_YIELD_POINT) {
					opCount = 0;
					if (db.yieldIfContendedSafely(SLEEP_AFTER_YIELD_DELAY)) {
						mDb = null;
						db = getDb();
						ypCount++;
					}
				}

				results[i] = operation.apply(this, results, i);
			}
			db.setTransactionSuccessful();
			return results;
		} finally {
			db.endTransaction();
		}
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		SelectionVars selectionVars = getFullDesc()
				.getSelectionVarsFromUri(uri);
		selection = ContentProviderHelper.joinSelection(selection,
				selectionVars.selection);
		selectionArgs = ContentProviderHelper.joinSelectionArgs(selectionArgs,
				selectionVars.selectionArgs);

		int result = getDb().delete(selectionVars.table, selection,
				selectionArgs);
		Uri notificationUri = getNotificationUri(uri);
		getContext().getContentResolver().notifyChange(notificationUri, null);
		return result;

	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		SelectionVars selectionVars = getFullDesc()
				.getSelectionVarsFromUri(uri);

		if (selectionVars.insertField != null
				&& !values.containsKey(selectionVars.insertField)) {
			values.put(selectionVars.insertField, selectionVars.insertValue);
		}

		ContractDesc desc = getFullDesc().getContractDesc(selectionVars.table);
		String guidField = desc.getGuidField();

		SQLiteDatabase db = getDb();

		boolean inGlobalTransaction = db.inTransaction();
		if (!inGlobalTransaction) {
			db.beginTransaction();
		}
		try {
			if (values.containsKey(guidField)) {
				String guid = values.getAsString(guidField);

				String idField = desc.getIdField();
				Cursor cursor = db.query(selectionVars.table,
						new String[] { idField }, guidField + "= ?",
						new String[] { guid }, null, null, null);
				try {
					if (cursor.moveToFirst()) {
						String id = cursor.getString(0);
						db.update(selectionVars.table, values,
								idField + " = ?", new String[] { id });

						Uri notificationUri = getNotificationUri(uri);
						getContext().getContentResolver().notifyChange(
								notificationUri, null);

						if (!inGlobalTransaction)
							db.setTransactionSuccessful();
						return Uri.withAppendedPath(uri, id);
					}
				} finally {
					cursor.close();
				}
			}
			long id = db.insertOrThrow(selectionVars.table, null, values);
			Uri notificationUri = getNotificationUri(uri);
			getContext().getContentResolver().notifyChange(notificationUri,
					null);
			if (!inGlobalTransaction)
				db.setTransactionSuccessful();
			return Uri.withAppendedPath(uri, Long.toString(id));
		} finally {
			if (!inGlobalTransaction)
				db.endTransaction();
		}
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();

		ContractFullDesc fullDesc = getFullDesc();
		SelectionVars selectionVars = fullDesc.getSelectionVarsFromUri(uri);

		StringBuilder sb = new StringBuilder();
		sb.append(selectionVars.table);
		fullDesc.addJoins(selectionVars.table, sb, projection);
		ProjectionMap projectionMap = fullDesc
				.getFinalProjectionMap(selectionVars.table);
		queryBuilder.setProjectionMap(projectionMap);
		queryBuilder.setTables(sb.toString());

		ContractDesc contractDesc = fullDesc
				.getContractDesc(selectionVars.table);
		String groupBy = contractDesc.getIdField();

		selection = ContentProviderHelper.joinSelection(selection,
				selectionVars.selection);
		selectionArgs = ContentProviderHelper.joinSelectionArgs(selectionArgs,
				selectionVars.selectionArgs);
		printQueryInDebug(queryBuilder, projection, selection, selectionArgs,
				groupBy, null, sortOrder);

		String limit = uri.getQueryParameter("limit");
		Cursor cursor = queryBuilder.query(getDb(), projection, selection,
				selectionArgs, groupBy, null, sortOrder, limit);

		Uri notificationUri = getNotificationUri(uri);
		cursor.setNotificationUri(getContext().getContentResolver(),
				notificationUri);
		return cursor;
	}

	private Uri getNotificationUri(Uri uri) {
		List<String> pathSegments = uri.getPathSegments();
		int segmenst = pathSegments.size();
		if (segmenst == 0)
			throw new IllegalArgumentException("Unknown URI " + uri);

		if (segmenst % 2 == 0) {
			String table = pathSegments.get(segmenst - 2);
			String id = pathSegments.get(segmenst - 1);
			return uri.buildUpon().path(table).appendPath(id).build();
		} else {
			String table = pathSegments.get(segmenst - 1);
			return uri.buildUpon().path(table).build();
		}
	}

	@SuppressWarnings("deprecation")
	@TargetApi(11)
	private void printQueryInDebug(SQLiteQueryBuilder queryBuilder,
			String[] projection, String selection, String[] selectionArgs,
			String groupBy, String having, String sortOrder) {
		if (BuildConfig.DEBUG) {
			String query;
			if (Build.VERSION.SDK_INT >= 11) {
				query = queryBuilder.buildQuery(projection, selection, groupBy,
						having, sortOrder, null);
			} else {
				query = queryBuilder.buildQuery(projection, selection,
						selectionArgs, groupBy, having, sortOrder, null);
			}
			Log.v(TAG, String.format("Quering: %s", query));
		}
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {

		SelectionVars selectionVars = getFullDesc()
				.getSelectionVarsFromUri(uri);
		selection = ContentProviderHelper.joinSelection(selection,
				selectionVars.selection);
		selectionArgs = ContentProviderHelper.joinSelectionArgs(selectionArgs,
				selectionVars.selectionArgs);
		int result = getDb().update(selectionVars.table, values, selection,
				selectionArgs);
		Uri notificationUri = getNotificationUri(uri);
		getContext().getContentResolver().notifyChange(notificationUri, null);
		return result;
	}

}