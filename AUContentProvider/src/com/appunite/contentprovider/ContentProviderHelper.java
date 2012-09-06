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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import android.provider.BaseColumns;
import android.text.TextUtils;

public class ContentProviderHelper {
	public static boolean isInProjection(String[] projection, String... columns) {
		if (projection == null) {
			return true;
		}

		// Optimized for a single-column test
		if (columns.length == 1) {
			String column = columns[0];
			for (String test : projection) {
				if (column.equals(test)) {
					return true;
				}
			}
		} else {
			for (String test : projection) {
				for (String column : columns) {
					if (column.equals(test)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static String[] concat(String[] first, Collection<String> second) {
		String[] ret = new String[first.length + second.size()];
		System.arraycopy(first, 0, ret, 0, first.length);
		int pos = 0;
		for (String string : second) {
			ret[pos] = string;
			pos++;
		}
		return ret;
	}

	public static String joinSelection(String selection1, String selection2) {
		if (TextUtils.isEmpty(selection1))
			return selection2;
		if (TextUtils.isEmpty(selection2))
			return selection1;
		return String.format("(%s) && (%s)", selection1, selection2);
	}
	
	public static String[] joinProjections(String[] projection1, String... projection2) {
		String[] ret = new String[projection1.length + projection2.length];
		System.arraycopy(projection1, 0, ret, 0, projection1.length);
		System.arraycopy(projection2, 0, ret, projection1.length, projection2.length);
		return ret;
	}

	public static String[] joinSelectionArgs(String[] selectionArgs1,
			Collection<String> selectionArgs2) {
		if (selectionArgs2 == null || selectionArgs2.size() == 0)
			return selectionArgs1;
		if (selectionArgs1 == null || selectionArgs1.length == 0)
			return selectionArgs2.toArray(new String[selectionArgs2.size()]);
		return concat(selectionArgs1, selectionArgs2);
	}

	public static boolean isInProjection(String[] projection,
			ProjectionMap projectionMap) {
		Set<String> projectionFields = new HashSet<String>(
				projectionMap.keySet());
		projectionFields.remove(BaseColumns._ID);

		for (String field : projection) {
			if (projectionFields.contains(field))
				return true;
		}
		return false;
	}

	public static String addIdExpressionToSelection(String selection,
			String field) {
		if (TextUtils.isEmpty(selection))
			return String.format("%s = ?", field);
		else
			return String.format("(%s) AND %s = ?", selection, field);
	}

	public static String[] addIdToSelectionArgs(String[] selectionArgs,
			String id) {
		if (selectionArgs == null || selectionArgs.length == 0)
			return new String[] { id };
		String[] newArgs = new String[selectionArgs.length + 1];
		for (int i = 0; i < selectionArgs.length; i++) {
			newArgs[i] = selectionArgs[i];
		}
		newArgs[selectionArgs.length] = id;
		return newArgs;
	}
}
