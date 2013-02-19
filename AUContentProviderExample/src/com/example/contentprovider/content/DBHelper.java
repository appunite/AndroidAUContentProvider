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

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.appunite.contentprovider.ContractDesc;
import com.appunite.contentprovider.ContractFullDesc;
import com.appunite.contentprovider.ContractDesc.FieldType;

public class DBHelper extends SQLiteOpenHelper {
	
	private static final String DB_NAME = "app.db";
	private static final int DB_VERSION = 1;
	
	static final ContractDesc DESC_BOOKS_DB_SQL = new ContractDesc.Builder(
			AppContract.Book.DB_TABLE, AppContract.Book.BOOK_ID,
			AppContract.Book.CONTENT_DIR_TYPE,
			AppContract.Book.CONTENT_ITEM_TYPE)
			.setGuidField(AppContract.Book.GUID)
			.addTableField(AppContract.Book.NAME, FieldType.TEXT)
			.addTableField(AppContract.Book.SYNC_TOKEN, FieldType.INTEGER)
			.addFakeField(AppContract.Book.AUTHORS_COUNT,
					"(SELECT count(*) FROM " + AppContract.Author.DB_TABLE +
					" WHERE " + AppContract.Author.BOOK_ID + " == " + AppContract.Book.BOOK_ID +
					")")
			.build();
	
	static final ContractDesc DESC_AUTHORS_DB_SQL = new ContractDesc.Builder(
			AppContract.Author.DB_TABLE, AppContract.Author.AUTHOR_ID,
			AppContract.Author.CONTENT_DIR_TYPE,
			AppContract.Author.CONTENT_ITEM_TYPE)
			.setGuidField(AppContract.Author.GUID)
			.addTableField(AppContract.Author.BOOK_ID, FieldType.INTEGER)
			.addTableField(AppContract.Author.NAME, FieldType.TEXT)
			.addTableField(AppContract.Author.SYNC_TOKEN, FieldType.INTEGER)
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
