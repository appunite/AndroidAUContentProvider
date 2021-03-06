/*
 * Copyright (C) 2010 The Android Open Source Project
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


/**
 * A convenience wrapper for a projection map. Makes it easier to create and use
 * projection maps.
 */
public class ProjectionMap extends HashMap<String, String> {
	private static final long serialVersionUID = 1L;

	public static class Builder {

		private ProjectionMap mMap = new ProjectionMap();

		public Builder add(String column) {
			mMap.putColumn(column, column);
			return this;
		}

		public Builder add(String alias, String expression) {
			mMap.putColumn(alias, expression + " AS " + alias);
			return this;
		}

		public Builder add(String alias, String table, String field) {
			return this.add(alias, String.format("%s.%s", table, field));
		}

		public Builder addAll(ProjectionMap map) {
			for (Map.Entry<String, String> entry : map.entrySet()) {
				mMap.putColumn(entry.getKey(), entry.getValue());
			}
			return this;
		}

		public ProjectionMap build() {
			String[] columns = new String[mMap.size()];
			mMap.keySet().toArray(columns);
			Arrays.sort(columns);
			mMap.mColumns = columns;
			return mMap;
		}

		public Builder addSum(String alias, String table, String field) {
			return this.add(alias, String.format("SUM(%s.%s)", table, field));
		}

		public Builder addNestedSum(String alias, String table, String idField,
				String nestedTable, String nestedIdField, String nestedSumField) {
			return this.add(alias, String.format(
					"(SELECT SUM(%s) FROM %s WHERE %s = %s)",
					DataHelper.field(nestedTable, nestedSumField), nestedTable,
					DataHelper.field(nestedTable, nestedIdField),
					DataHelper.field(table, idField)));
		}

	}

	private String[] mColumns;

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Returns a sorted array of all column names in the projection map.
	 */
	public String[] getColumnNames() {
		return mColumns;
	}

	private void putColumn(String alias, String column) {
		super.put(alias, column);
	}

	@Override
	public String put(String key, String value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void putAll(Map<? extends String, ? extends String> map) {
		throw new UnsupportedOperationException();
	}
}
