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
package com.example.contentprovider.content;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

import com.appunite.contentprovider.ContractDesc;
import com.appunite.contentprovider.ContractFullDesc;
import com.appunite.contentprovider.OnDeleteTrigger;
import com.appunite.contentprovider.ContractFullDesc.SelectionVars;
import com.appunite.contentprovider.OnInsertTrigger;
import com.appunite.contentprovider.OnUpdateTrigger;
import com.appunite.contentprovider.ContractDesc.FieldType;
import com.appunite.contentprovider.QueryInterface;

public class DBHelper extends SQLiteOpenHelper {
	
	private static final String DB_NAME = "app.db";
	private static final int DB_VERSION = 1;
	
	/**
	 *  A little bit of magic - just ignore at start point
	 *  
	 *  This is a sql that will be used as field value
	 */
	static final String BOOKS_AUTHORS_COUNT_SQL = "(" +
			"SELECT count(*) FROM " + AppContract.Author.DB_TABLE +
			" WHERE " + AppContract.Author.BOOK_ID + " == " + AppContract.Book.BOOK_ID +
			")";
	
	/**
	 * A little bit of magic - just ignore at start point
	 * 
	 * This will create a trigger that delete all authors from book you are
	 * deleting
	 */
	static final OnDeleteTrigger ON_BOOK_DELETE = new OnDeleteTrigger() {

		@Override
		public void onDelete(QueryInterface queryInterface, Uri uri,
				SelectionVars selectionVars, String selection,
				String[] selectionArgs) {
			String[] PROJECTION = new String[] { AppContract.Book.BOOK_ID };
			Cursor cursor = queryInterface.query(
					AppContract.Book.DB_TABLE, PROJECTION, selection,
					selectionArgs, null, null, null);
			try {
				for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor
						.moveToNext()) {
					long bookId = cursor.getLong(0);
					queryInterface.delete(AppContract.Author.DB_TABLE,
							AppContract.Author.BOOK_ID + " = ?",
							new String[] { String.valueOf(bookId) });
				}
			} finally {
				cursor.close();
			}
		}
	};
	
	/**
	 * A little bit of magic - just ignore at start point
	 * 
	 * This will setup CREATED_AT and UPDATED_AT when you isert a row
	 */
	static final OnInsertTrigger ON_AUTHOR_INSERT = new OnInsertTrigger() {
		
		@Override
		public Uri onInsert(QueryInterface queryInterface, Uri uri,
				SelectionVars table, ContentValues newValues) {
			long now = System.currentTimeMillis();
			newValues.put(AppContract.Author.CREATED_AT, now);
			newValues.put(AppContract.Author.UPDATED_AT, now);
			return null;
		}
	};

	/**
	 * A little bit of magic - just ignore at start point
	 * 
	 * This will setup UPDATED_AT when you update a row
	 */
	static final OnUpdateTrigger ON_AUTHOR_UPDATE = new OnUpdateTrigger() {
	
		@Override
		public void onUpdate(QueryInterface queryInterface, Uri uri,
				SelectionVars selectionVars, ContentValues values,
				String selection, String[] selectionArgs) {
			long now = System.currentTimeMillis();
			values.put(AppContract.Author.UPDATED_AT, now);
		}
	
	};

	/**
	 * More magic - just ignore at start point
	 * 
	 * This is example of general class that can help you when you want to have
	 * CREATED_AT and UPDATED_AT always fresh for more than one table
	 */
	private static class AutoCratedAt implements OnInsertTrigger, OnUpdateTrigger {
		
		private final String mCreatedAtField;
		private final String mUpdatedAtField;
		public AutoCratedAt(String createdAtField, String updatedAtField) {
			mCreatedAtField = createdAtField;
			mUpdatedAtField = updatedAtField;
		}

		@Override
		public Uri onInsert(QueryInterface queryInterface, Uri uri,
				SelectionVars table, ContentValues newValues) {
			long now = System.currentTimeMillis();
			newValues.put(mCreatedAtField, now);
			newValues.put(mUpdatedAtField, now);
			return null;
		}

		@Override
		public void onUpdate(QueryInterface queryInterface, Uri uri,
				SelectionVars selectionVars, ContentValues values,
				String selection, String[] selectionArgs) {
			long now = System.currentTimeMillis();
			values.put(mUpdatedAtField, now);
		}
		
	}
	
	static final ContractDesc DESC_BOOKS_DB_SQL = new ContractDesc.Builder(
			AppContract.Book.DB_TABLE, AppContract.Book.BOOK_ID,
			AppContract.Book.CONTENT_DIR_TYPE,
			AppContract.Book.CONTENT_ITEM_TYPE)
			.setGuidField(AppContract.Book.GUID)
			.addTableField(AppContract.Book.NAME, FieldType.TEXT)
			.addTableField(AppContract.Book.SYNC_TOKEN, FieldType.INTEGER)
			.addTableField(AppContract.Book.CREATED_AT, FieldType.INTEGER)
			.addTableField(AppContract.Book.UPDATED_AT, FieldType.INTEGER)
			.addFakeField(AppContract.Book.AUTHORS_COUNT, BOOKS_AUTHORS_COUNT_SQL)
			.addOnInsertTrigger(new AutoCratedAt(AppContract.Book.CREATED_AT, AppContract.Book.UPDATED_AT))
			.addOnUpdateTrigger(new AutoCratedAt(AppContract.Book.CREATED_AT, AppContract.Book.UPDATED_AT))
			.addOnDeleteTrigger(ON_BOOK_DELETE)
			.build();
	
	static final ContractDesc DESC_AUTHORS_DB_SQL = new ContractDesc.Builder(
			AppContract.Author.DB_TABLE, AppContract.Author.AUTHOR_ID,
			AppContract.Author.CONTENT_DIR_TYPE,
			AppContract.Author.CONTENT_ITEM_TYPE)
			.setGuidField(AppContract.Author.GUID)
			.addTableField(AppContract.Author.BOOK_ID, FieldType.INTEGER)
			.addTableField(AppContract.Author.NAME, FieldType.TEXT)
			.addTableField(AppContract.Author.SYNC_TOKEN, FieldType.INTEGER)
			.addTableField(AppContract.Author.CREATED_AT, FieldType.INTEGER)
			.addTableField(AppContract.Author.UPDATED_AT, FieldType.INTEGER)
			.addOnInsertTrigger(ON_AUTHOR_INSERT)
			.addOnUpdateTrigger(ON_AUTHOR_UPDATE)
			.build();
	
	static final ContractFullDesc FULL_DESC = new ContractFullDesc.Builder(
			AppContract.AUTHORITY)
			.addTable(DESC_BOOKS_DB_SQL)
			.addTable(DESC_AUTHORS_DB_SQL)
			.addConnection1n(DESC_BOOKS_DB_SQL, AppContract.Book.BOOK_ID,
					DESC_AUTHORS_DB_SQL, AppContract.Author.BOOK_ID)
			.build();
	
	public DBHelper(Context context) {
		super(context, DB_NAME, null, DB_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		FULL_DESC.sqlCreateAll(db);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		FULL_DESC.sqlDropAll(db);
		onCreate(db);
	}

}
