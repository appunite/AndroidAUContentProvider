/*
 * Copyright (C) 2013 Jacek Marchwicki <jacek.marchwicki@gmail.com>
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
 * limitations under the License
 */

package com.appunite.contentprovider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import com.appunite.contentprovider.ContractFullDesc.SelectionVars;

import android.annotation.TargetApi;
import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

public abstract class BaseContentProvider extends ContentProvider implements
		QueryInterface {

	private static final int MAX_OPERATIONS_PER_YIELD_POINT = 500;
	private static final int MIN_OPERATIONS_PER_YIELD_POINT = 250;
	private static final int FORCE_OPERATIONS_PER_YIELD_POINT = 400;
	private static final long SLEEP_AFTER_YIELD_DELAY = 4000;
	private static final String TAG = "BaseContentProvider";
	private static final boolean DEBUG = BuildConfig.DEBUG;

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
				selectionVars.getSelection());
		selectionArgs = ContentProviderHelper.joinSelectionArgs(selectionArgs,
				selectionVars.getSelectionArgs());
		ContractDesc contractDesc = selectionVars.getContractDesc();
		for (OnDeleteTrigger trigger : contractDesc.mOnDeleteTriggers) {
			trigger.onDelete(this, uri, selectionVars, selection, selectionArgs);
		}
		int result = delete(selectionVars.getTable(), selection, selectionArgs);
		for (OnAfterDeleteTrigger trigger : contractDesc.mOnAfterDeleteTriggers) {
			trigger.onDelete(this, uri, selectionVars, selection, selectionArgs);
		}
		return result;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		SelectionVars selectionVars = getFullDesc()
				.getSelectionVarsFromUri(uri);

		String insertField = selectionVars.getInsertField();
		if (insertField != null && !values.containsKey(insertField)) {
			values.put(insertField, selectionVars.getInsertValue());
		}

		ContractDesc contractDesc = selectionVars.getContractDesc();
		ArrayList<OnInsertTrigger> triggers = contractDesc.mOnInsertTriggers;
		ArrayList<OnAfterInsertTrigger> afterTriggers = contractDesc.mOnAfterInsertTriggers;

		SQLiteDatabase db = getDb();

		boolean doLocalTransaction = !db.inTransaction()
				&& (triggers.size() > 0 || afterTriggers.size() > 0);
		if (doLocalTransaction) {
			db.beginTransaction();
		}
		try {
			for (OnInsertTrigger trigger : triggers) {
				Uri newUri = trigger.onInsert(this, uri, selectionVars, values);
				if (newUri != null) {
					return newUri;
				}
			}
			String table = selectionVars.getTable();
			long id = insertOrThrow(table, null, values);
			Uri newUri = Uri.withAppendedPath(uri, Long.toString(id));
			for (OnAfterInsertTrigger trigger : afterTriggers) {
				trigger.onInsert(this, newUri, selectionVars, id, values);
			}
			return newUri;
		} finally {
			if (doLocalTransaction) {
				db.endTransaction();
			}
		}
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();

		ContractFullDesc fullDesc = getFullDesc();
		SelectionVars selectionVars = fullDesc.getSelectionVarsFromUri(uri);

		StringBuilder sb = new StringBuilder();
		String table = selectionVars.getTable();
		sb.append(table);
		fullDesc.addJoins(table, sb, projection);
		ProjectionMap projectionMap = fullDesc.getFinalProjectionMap(table);
		queryBuilder.setProjectionMap(projectionMap);
		queryBuilder.setTables(sb.toString());

		ContractDesc contractDesc = fullDesc.getContractDesc(table);
		String groupBy = contractDesc.getIdField();

		selection = ContentProviderHelper.joinSelection(selection,
				selectionVars.getSelection());
		selectionArgs = ContentProviderHelper.joinSelectionArgs(selectionArgs,
				selectionVars.getSelectionArgs());
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

	private Uri getNotificationUri(String table) {
		String authority = getFullDesc().mAuthority;
		return Uri.parse("content://" + authority + "/" + table);
	}

	@SuppressWarnings("deprecation")
	@TargetApi(11)
	private void printQueryInDebug(SQLiteQueryBuilder queryBuilder,
			String[] projection, String selection, String[] selectionArgs,
			String groupBy, String having, String sortOrder) {
		if (DEBUG) {
			String query;
			if (Build.VERSION.SDK_INT >= 11) {
				query = queryBuilder.buildQuery(projection, selection, groupBy,
						having, sortOrder, null);
			} else {
				query = queryBuilder.buildQuery(projection, selection,
						selectionArgs, groupBy, having, sortOrder, null);
			}
			Log.v(TAG, query);
		}
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {

		SelectionVars selectionVars = getFullDesc()
				.getSelectionVarsFromUri(uri);
		selection = ContentProviderHelper.joinSelection(selection,
				selectionVars.getSelection());
		selectionArgs = ContentProviderHelper.joinSelectionArgs(selectionArgs,
				selectionVars.getSelectionArgs());

		String table = selectionVars.getTable();
		ContractDesc contractDesc = selectionVars.getContractDesc();
		ArrayList<OnUpdateTrigger> triggers = contractDesc.mOnUpdateTriggers;
		ArrayList<OnAfterUpdateTrigger> afterTriggers = contractDesc.mOnAfterUpdateTriggers;
		SQLiteDatabase db = getDb();

		boolean doLocalTransaction = !db.inTransaction() && triggers.size() > 0;
		if (doLocalTransaction) {
			db.beginTransaction();
		}
		try {
			for (OnUpdateTrigger trigger : triggers) {
				trigger.onUpdate(this, uri, selectionVars, values, selection,
						selectionArgs);
			}

			int result = update(table, values, selection, selectionArgs);
			for (OnAfterUpdateTrigger trigger : afterTriggers) {
				trigger.onUpdate(this, uri, selectionVars, values, selection, selectionArgs);
			}
			return result;
		} finally {
			if (doLocalTransaction) {
				db.endTransaction();
			}
		}
	}

	@Override
	public Cursor query(String table, String[] columns, String selection,
			String[] selectionArgs, String groupBy, String having,
			String orderBy) {
		return query(false, table, columns, selection, selectionArgs, groupBy,
				having, orderBy, null);
	}

	@Override
	public Cursor query(boolean distinct, String table, String[] columns,
			String selection, String[] selectionArgs, String groupBy,
			String having, String orderBy, String limit) {
		if (DEBUG) {
			StringBuilder sb = new StringBuilder();
			sb.append("SELECT ");
			if (distinct) {
				sb.append(" DISTINCT ");
			}
			boolean next = false;
			for (String column : columns) {
				if (next) {
					sb.append(", ");
				}
				sb.append(column);
			}
			sb.append(" FROM ").append(table);
			if (selection != null) {
				sb.append(" WHERE ").append(selection);
			}
			if (groupBy != null) {
				sb.append(" GROUP BY ").append(groupBy);
			}
			if (having != null) {
				sb.append(" HAVING ").append(having);
			}
			if (limit != null) {
				sb.append(" LIMIT ").append(limit);
			}
			if (selectionArgs != null) {
				sb.append(", Selection args: ");
				for (String string : selectionArgs) {
					sb.append(string).append(", ");
				}
			}

			Log.v(TAG, sb.toString());
		}
		return getDb().query(distinct, table, columns, selection,
				selectionArgs, groupBy, having, orderBy, limit);
	}

	@Override
	public int update(String table, ContentValues values, String selection,
			String[] selectionArgs) {
		if (DEBUG) {
			StringBuilder sbArgs = new StringBuilder();
			StringBuilder sb = new StringBuilder();
			sb.append("UPDATE ").append(table).append(" SET ");
			Set<Entry<String, Object>> valueSet = values.valueSet();
			boolean next = false;
			for (Entry<String, Object> entry : valueSet) {
				if (next) {
					sb.append(", ");
				}
				next = true;
				sb.append(entry.getKey()).append("=?");
				sbArgs.append(entry.getValue()).append(", ");
			}
			if (selection != null) {
				sb.append(" WHERE ").append(selection);
			}

			sb.append(", Args: ").append(sbArgs.toString());

			if (selectionArgs != null) {
				sb.append(", Selection args: ");
				for (String string : selectionArgs) {
					sb.append(string).append(", ");
				}
			}

			Log.v(TAG, sb.toString());
		}
		int count = getDb().update(table, values, selection, selectionArgs);
		Uri notificationUri = getNotificationUri(table);
		getContext().getContentResolver().notifyChange(notificationUri, null);
		return count;
	}

	@Override
	public long insertOrThrow(String table, String nullColumnHack,
			ContentValues values) throws SQLException {
		if (DEBUG) {
			StringBuilder sbArgs = new StringBuilder();
			StringBuilder sb = new StringBuilder();
			sb.append("INSERT INTO ").append(table).append(" (");
			Set<Entry<String, Object>> valueSet = values.valueSet();
			boolean next = false;
			int count = 0;
			for (Entry<String, Object> entry : valueSet) {
				if (next) {
					sb.append(", ");
				}
				next = true;
				sb.append(entry.getKey());
				count++;
				sbArgs.append(entry.getValue()).append(", ");
			}
			sb.append(") VALUES (");
			for (int i = 0; i < count; ++i) {
				if (i > 0) {
					sb.append(", ");
				}
				sb.append("?");
			}
			sb.append(")").append(", Args: ").append(sbArgs.toString());
			Log.v(TAG, sb.toString());
		}
		long id = getDb().insertOrThrow(table, nullColumnHack, values);
		Uri notificationUri = getNotificationUri(table);
		getContext().getContentResolver().notifyChange(notificationUri, null);
		return id;
	}

	@Override
	public int delete(String table, String whereClause, String[] whereArgs) {
		if (DEBUG) {
			StringBuilder sb = new StringBuilder();
			sb.append("DELETE FROM ").append(table);
			if (whereClause != null) {
				sb.append(" WHERE ").append(whereClause);
			}
			if (whereArgs != null) {
				sb.append(", Selection args: ");
				for (String string : whereArgs) {
					sb.append(string).append(", ");
				}
			}
			Log.v(TAG, sb.toString());
		}
		int count = getDb().delete(table, whereClause, whereArgs);
		Uri notificationUri = getNotificationUri(table);
		getContext().getContentResolver().notifyChange(notificationUri, null);
		return count;
	}

}