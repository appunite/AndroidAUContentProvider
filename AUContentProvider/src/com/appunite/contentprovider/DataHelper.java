/*
 * Copyright (C) 2012 Jacek Marchwicki <jacek.marchwicki@gmail.com>
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

public class DataHelper {
	public static String field(String tableName, String fieldName) {
		return String.format("%s_%s", tableName, fieldName);
	}
	
	public static String contentItemType(String authority, String table) {
		return String.format("vnd.android.cursor.item/%s.%s", authority, table);
	}
	
	public static String contentType(String authority, String table) {
		return String.format("vnd.android.cursor.dir/%s.%s", authority, table);
	}

	public static String createBinaryUniqueIndexIfNotExist(String tableName,
			String columnName) {
		String indexName = String.format("%s_%s", tableName, columnName);
		return String.format("CREATE UNIQUE INDEX IF NOT EXISTS %s ON %s (%s COLLATE BINARY ASC)",
				indexName, tableName, columnName);
	}
	
}
