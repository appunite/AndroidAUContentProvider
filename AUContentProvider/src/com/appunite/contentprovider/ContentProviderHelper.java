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

import java.util.HashSet;
import java.util.Set;


import android.provider.BaseColumns;

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

	public static boolean isInProjection(String[] projection,
			ProjectionMap projectionMap) {
		Set<String> projectionFields = new HashSet<String>( projectionMap.keySet());
		projectionFields.remove(BaseColumns._ID);
		
		for (String field : projection) {
			if (projectionFields.contains(field))
				return true;
		}
		return false;
	}
}
