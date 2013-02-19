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

import com.appunite.contentprovider.DataHelper;

import android.net.Uri;
import android.provider.BaseColumns;

public class AppContract {

	public static final String AUTHORITY = "com.example.contentprovider";
	public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);
	
	public static class Book implements BaseColumns {
		public static final String CONTENT_PATH = "books";
		public static final Uri CONTENT_URI = Uri.withAppendedPath(
				AUTHORITY_URI, CONTENT_PATH);

		public static final String CONTENT_ITEM_TYPE = DataHelper
				.contentItemType(AUTHORITY, CONTENT_PATH);
		public static final String CONTENT_DIR_TYPE = DataHelper.contentType(
				AUTHORITY, CONTENT_PATH);

		static final String DB_TABLE = CONTENT_PATH;

		public static final String BOOK_ID = DataHelper.field(DB_TABLE, _ID);
		public static final String GUID = DataHelper.field(DB_TABLE, "guid");
		public static final String NAME = DataHelper.field(DB_TABLE, "name");
		public static final String AUTHORS_COUNT = DataHelper.field(DB_TABLE, "authors_count");
		public static final String SYNC_TOKEN = DataHelper.field(DB_TABLE, "sync_token");
		public static final String CREATED_AT = DataHelper.field(DB_TABLE, "created_at");
		public static final String UPDATED_AT = DataHelper.field(DB_TABLE, "updated_at");
	}
	
	public static class Author implements BaseColumns {
		public static final String CONTENT_PATH = "authors";
		public static final Uri CONTENT_URI = Uri.withAppendedPath(
				AUTHORITY_URI, CONTENT_PATH);

		public static final String CONTENT_ITEM_TYPE = DataHelper
				.contentItemType(AUTHORITY, CONTENT_PATH);
		public static final String CONTENT_DIR_TYPE = DataHelper.contentType(
				AUTHORITY, CONTENT_PATH);

		static final String DB_TABLE = CONTENT_PATH;

		public static final String AUTHOR_ID = DataHelper.field(DB_TABLE, _ID);
		public static final String BOOK_ID = DataHelper.field(DB_TABLE, "book_id");
		public static final String GUID = DataHelper.field(DB_TABLE, "guid");
		public static final String NAME = DataHelper.field(DB_TABLE, "name");
		public static final String SYNC_TOKEN = DataHelper.field(DB_TABLE, "sync_token");
		public static final String CREATED_AT = DataHelper.field(DB_TABLE, "created_at");
		public static final String UPDATED_AT = DataHelper.field(DB_TABLE, "updated_at");
	}

}
