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

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class ContractDesc {
	
	private final String mContentType;
	private final String mContentItemType;
	private final String mTableName;
	private final String mIdField;
	private String mGuidField = null;
	
	boolean mIsFts = false;
	final List<TableFieldDesc> mTableFieldDescs = new ArrayList<ContractDesc.TableFieldDesc>();
	final List<FakeFieldDesc> mFakeFieldsDescs = new ArrayList<ContractDesc.FakeFieldDesc>();
	ArrayList<OnInsertTrigger> mOnInsertTriggers = new ArrayList<OnInsertTrigger>();
	ArrayList<OnUpdateTrigger> mOnUpdateTriggers = new ArrayList<OnUpdateTrigger>();
	ArrayList<OnDeleteTrigger> mOnDeleteTriggers = new ArrayList<OnDeleteTrigger>();
	ArrayList<OnAfterInsertTrigger> mOnAfterInsertTriggers = new ArrayList<OnAfterInsertTrigger>();
	ArrayList<OnAfterUpdateTrigger> mOnAfterUpdateTriggers = new ArrayList<OnAfterUpdateTrigger>();
	ArrayList<OnAfterDeleteTrigger> mOnAfterDeleteTriggers = new ArrayList<OnAfterDeleteTrigger>();
	
	public enum FieldType {
		TEXT("TEXT"), INTEGER("INTEGER"), REAL("REAL"), BLOB("BLOB");

		private String mTypeString;

		FieldType(String typeString) {
			mTypeString = typeString;
		}

		public String getTypeString() {
			return mTypeString;
		}
	}
	
	private static class FakeFieldDesc {

		private final String fieldName;
		private final String sql;

		public FakeFieldDesc(String fieldName, String sql) {
			this.fieldName = fieldName;
			this.sql = sql;
		}
		
	}
	
	private static class TableFieldDesc {

		public final String fieldName;
		public final FieldType fieldType;
		private boolean notNull;

		public TableFieldDesc(String fieldName, FieldType fieldType) {
			this.fieldName = fieldName;
			this.fieldType = fieldType;
			this.notNull = false;
		}
		
		public TableFieldDesc(String fieldName, FieldType fieldType, boolean notNull) {
			this.fieldName = fieldName;
			this.fieldType = fieldType;
			this.notNull = notNull;
		}
		
	}

	protected ContractDesc(String tableName, String idField, String contentType, String contentItemType) {
		this.mTableName = tableName;
		this.mIdField = idField;
		this.mContentType = contentType;
		this.mContentItemType = contentItemType;
	}
	
	public static class Builder {
		private ContractDesc mContractDesc;
		private Set<String> mFields; 
		
		public Builder(String tableName, String idField, String contentType, String contentItemType) {
			mContractDesc = new ContractDesc(tableName, idField, contentType, contentItemType);
			mFields = new HashSet<String>();;
			mFields.add(idField);
		}
		
		private void checkIfFieldExistAndAdd(String fieldName) {
			if (mFields.contains(fieldName)) {
				throw new IllegalArgumentException(String.format(
						"Field %s already exist in table", fieldName));
			}
			mFields.add(fieldName);
		}
		
		public Builder addTableField(String fieldName, FieldType fieldType) {
			checkIfFieldExistAndAdd(fieldName);
			mContractDesc.mTableFieldDescs.add(new TableFieldDesc(fieldName, fieldType));
			return this;
		}

		
		public Builder addTableNotNullField(String fieldName, FieldType fieldType) {
			checkIfFieldExistAndAdd(fieldName);
			mContractDesc.mTableFieldDescs.add(new TableFieldDesc(fieldName, fieldType, true));
			return this;
		}
		
		public Builder setGuidField(String guidFieldName) {
			if (mContractDesc.mGuidField != null)
				throw new IllegalArgumentException("Guid field already set");
			mContractDesc.mGuidField = guidFieldName;
			mContractDesc.mOnInsertTriggers.add(new GuidOnInsertTrigger(guidFieldName));
			return this.addTableField(guidFieldName, FieldType.TEXT);
		}
		
		public Builder addFakeField(String fieldName, String sql) {
			checkIfFieldExistAndAdd(fieldName);
			FakeFieldDesc fakeFieldDesc = new FakeFieldDesc(fieldName, sql);
			mContractDesc.mFakeFieldsDescs.add(fakeFieldDesc);
			return this;
		}
		
		public Builder addOnInsertTrigger(OnInsertTrigger onInsertTrigger) {
			mContractDesc.mOnInsertTriggers.add(onInsertTrigger);
			return this;
		}
		
		public Builder addOnUpdateTrigger(OnUpdateTrigger onUpdateTrigger) {
			mContractDesc.mOnUpdateTriggers.add(onUpdateTrigger);
			return this;
		}
		
		public Builder addOnDeleteTrigger(OnDeleteTrigger onDeleteTrigger) {
			mContractDesc.mOnDeleteTriggers.add(onDeleteTrigger);
			return this;
		}
		
		public Builder addOnAfterInsertTrigger(OnAfterInsertTrigger onAfterInsertTrigger) {
			mContractDesc.mOnAfterInsertTriggers.add(onAfterInsertTrigger);
			return this;
		}
		
		public Builder addOnAfterUpdateTrigger(OnAfterUpdateTrigger onAfterUpdateTrigger) {
			mContractDesc.mOnAfterUpdateTriggers.add(onAfterUpdateTrigger);
			return this;
		}
		
		public Builder addOnAfterDeleteTrigger(OnAfterDeleteTrigger onAfterDeleteTrigger) {
			mContractDesc.mOnAfterDeleteTriggers.add(onAfterDeleteTrigger);
			return this;
		}
		
		public ContractDesc build() {
			return mContractDesc;
		}
		
		public ContractDesc buildFts() {
			mContractDesc.mIsFts  = true;
			return mContractDesc;
		}
	}
	
	public ProjectionMap buildProjectionMap() {
		ProjectionMap.Builder builder = new ProjectionMap.Builder().add(BaseColumns._ID,
				mIdField).add(mIdField);
		Iterator<TableFieldDesc> fieldsIter = mTableFieldDescs.iterator();
		while (fieldsIter.hasNext()) {
			TableFieldDesc field = fieldsIter.next();
			builder.add(field.fieldName);
		}
		Iterator<FakeFieldDesc> fakeFieldsIter = mFakeFieldsDescs.iterator();
		while (fakeFieldsIter.hasNext()) {
			FakeFieldDesc fakeFieldDesc = fakeFieldsIter.next();
			builder.add(fakeFieldDesc.fieldName, fakeFieldDesc.sql);
		}
		return builder.build();
	}
	
	
	
	private String sqlDropTableQuery() {
		return String.format("DROP TABLE IF EXISTS %s", mTableName);
	}
	
	private String sqlCreateTableQuery() {
		StringBuilder sb = new StringBuilder();
		if (mIsFts) 
			sb.append("CREATE VIRTUAL TABLE ");
		else
			sb.append("CREATE TABLE ");
		sb.append(mTableName);
		if (mIsFts)
			sb.append(" USING fts3 ");
		sb.append(" (");
		sb.append(String.format("%s %s primary key autoincrement, ", mIdField,
				FieldType.INTEGER.getTypeString()));
		
		Iterator<TableFieldDesc> fieldsIter = mTableFieldDescs.iterator();
		while (fieldsIter.hasNext()) {
			TableFieldDesc field = fieldsIter.next();
			sb.append(String.format("%s %s ", field.fieldName, field.fieldType.getTypeString()));
			if (field.notNull) {
				sb.append("NOT NULL ");
			}
			if (fieldsIter.hasNext())
				sb.append(", ");
		}
		sb.append(" );");
		return sb.toString();
	}
	
	public void sqlDropTable(SQLiteDatabase db) {
		db.execSQL(sqlDropTableQuery());
	}
	
	public void sqlCreateTable(SQLiteDatabase db) {
		db.execSQL(sqlCreateTableQuery());
		if (!mIsFts) {
			if (mGuidField != null) {
				String sql = DataHelper.createBinaryUniqueIndexIfNotExist(
						mTableName, mGuidField);
				db.execSQL(sql);
			}
		}
	}
	
	@Deprecated
	public String sqlDropTable() {
		return sqlDropTableQuery();
	}
	
	@Deprecated
	public String sqlCreateTable() {
		return sqlDropTableQuery();
	}
	
	
	public String getTableName() {
		return mTableName;
	}

	public String getIdField() {
		return mIdField;
	}
	
	public String getContentType() {
		return mContentType;
	}

	public String getContentItemType() {
		return mContentItemType;
	}

	public Collection<String> getFieldsWithId() {
		Collection<String> fields = new ArrayList<String>();
		for (TableFieldDesc desc : mTableFieldDescs) {
			fields.add(desc.fieldName);
		}
		for (FakeFieldDesc desc : mFakeFieldsDescs) {
			fields.add(desc.fieldName);
		}
		fields.add(mIdField);
		return fields;
	}
	
	public String getGuidField() {
		return mGuidField ;
	}
}
