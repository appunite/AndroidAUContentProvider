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

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import com.appunite.contentprovider.ContractFullDesc.SelectionVars;

class GuidOnInsertTrigger implements OnInsertTrigger {

	private final String mGuid;

	public GuidOnInsertTrigger(String guid) {
		mGuid = guid;
	}

	@Override
	public Uri onInsert(QueryInterface queryInterface, Uri uri,
			SelectionVars selectionVars, ContentValues newValues) {
		ContractDesc contractDesc = selectionVars.getContractDesc();
		if (!newValues.containsKey(mGuid)) {
			return null;
		}
		String guid = newValues.getAsString(mGuid);

		String idField = contractDesc.getIdField();
		String table = selectionVars.getTable();

		Cursor cursor = queryInterface.query(false, table, new String[] { idField },
				mGuid + " = ?", new String[] { guid }, null, null, null, "1");
		try {
			if (!cursor.moveToFirst()) {
				return null;
			}
			String id = cursor.getString(0);
			String selection = idField + " = ?";
			String[] selectionArgs = new String[] { id };
			ArrayList<OnUpdateTrigger> triggers = contractDesc.mOnUpdateTriggers;
			for (OnUpdateTrigger trigger : triggers) {
				trigger.onUpdate(queryInterface, uri, selectionVars, newValues,
						selection, selectionArgs);
			}
			queryInterface.update(table, newValues, selection, selectionArgs);

			return Uri.withAppendedPath(uri, id);
		} finally {
			cursor.close();
		}
	}

}