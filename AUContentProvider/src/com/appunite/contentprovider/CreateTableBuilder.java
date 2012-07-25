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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CreateTableBuilder {

	List<String> mFields;
	private final String mTableName;

	public enum FieldType {
		TEXT("TEXT"), INTEGER("INTEGER"), REAL("REAL");

		private String mTypeString;

		FieldType(String typeString) {
			mTypeString = typeString;
		}

		public String getTypeString() {
			return mTypeString;
		}
	}

	public CreateTableBuilder(String tableName) {
		this.mTableName = tableName;
		mFields = new ArrayList<String>();
	}

	public CreateTableBuilder addPrimaryKey(String fieldName) {
		mFields.add(String.format("%s %s primary key autoincrement", fieldName,
				FieldType.INTEGER.getTypeString()));
		return this;
	}

	public CreateTableBuilder addField(String fieldName, FieldType type,
			boolean notNull) {
		if (notNull) {
			mFields.add(String.format("%s %s NOT NULL", fieldName,
					type.getTypeString()));
		} else {
			mFields.add(String.format("%s %s", fieldName, type.getTypeString()));
		}
		return this;
	}

	public String build() {
		StringBuilder sb = new StringBuilder();
		sb.append("CREATE TABLE ");
		sb.append(mTableName);
		sb.append(" (");
		Iterator<String> fieldsIter = mFields.iterator();
		while (fieldsIter.hasNext()) {
			String field = fieldsIter.next();
			sb.append(field);
			if (fieldsIter.hasNext())
				sb.append(", ");
		}
		sb.append(" );");
		return sb.toString();
	}
}
