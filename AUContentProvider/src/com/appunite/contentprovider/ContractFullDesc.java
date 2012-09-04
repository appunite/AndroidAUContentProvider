package com.appunite.contentprovider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;

public class ContractFullDesc {
	private static final String TAG = ConnectionDesc.class.getCanonicalName();

	private ContractFullDesc() {

	}

	private final Collection<ConnectionDesc> mConnectionDescs = new ArrayList<ContractFullDesc.ConnectionDesc>();
	public String mAuthority;
	private Map<String, ContractDesc> mTables = new HashMap<String, ContractDesc>();
	private Map<String, String> mFields = new HashMap<String, String>();
	private Map<String, ProjectionMap> mFinalProjectionMaps = new HashMap<String, ProjectionMap>();
	private Map<String, ProjectionMap> mSimpleProjectionMaps = new HashMap<String, ProjectionMap>();
	
	public ContractDesc getContractDesc(String tableName) {
		return mTables.get(tableName);
	}

	public ProjectionMap getFinalProjectionMap(String tableName) {
		ProjectionMap projectionMap = mFinalProjectionMaps.get(tableName);
		if (projectionMap != null)
			return projectionMap;

		ProjectionMap.Builder builder = new ProjectionMap.Builder();
		for (Iterator<String> iterator = mSimpleProjectionMaps.keySet()
				.iterator(); iterator.hasNext();) {
			String simpleTable = iterator.next();
			if (simpleTable.equals(tableName))
				continue;
			builder.addAll(mSimpleProjectionMaps.get(simpleTable));
		}
		ProjectionMap tableProjectionMap = mSimpleProjectionMaps.get(tableName);
		if (tableProjectionMap == null)
			throw new IllegalArgumentException("Unkonwon table " + tableName);
		builder.addAll(tableProjectionMap);
		projectionMap = builder.build();
		
		mFinalProjectionMaps.put(tableName, projectionMap);
		return projectionMap;
	}

	private static class ConnectionDesc {
		String table1;
		String field1;
		String tableN;
		String fieldN;

		@Override
		public String toString() {
			return String.format("1:N %s->%s (%s == %s)", table1, tableN,
					field1, fieldN);
		}
	}

	public static class Builder {
		private ContractFullDesc mDesc;

		public Builder(String authority) {
			mDesc = new ContractFullDesc();
			mDesc.mAuthority = authority;
		}

		public Builder addTable(ContractDesc table) {
			mDesc.addTable(table);
			return this;
		}

		public Builder addConnection1n(ContractDesc desc1, String field1,
				ContractDesc descN, String fieldN) {
			mDesc.addConnection1n(desc1, field1, descN, fieldN);
			return this;
		}

		public ContractFullDesc build() {
			return mDesc;
		}
	}

	public void addConnection1n(ContractDesc desc1, String field1,
			ContractDesc descN, String fieldN) {
		ConnectionDesc desc = new ConnectionDesc();
		desc.table1 = desc1.getTableName();
		desc.field1 = field1;
		desc.tableN = descN.getTableName();
		desc.fieldN = fieldN;
		mConnectionDescs.add(desc);
	}

	public void addTable(ContractDesc table) {
		String tableName = table.getTableName();
		mTables.put(tableName, table);
		mSimpleProjectionMaps.put(tableName, table.buildProjectionMap());
		Collection<String> fields = table.getFieldsWithId();
		for (String field : fields) {
			mFields.put(field, tableName);
		}
	}

	public static class SelectionVars {
		public String table;
		public String selection = null;
		public Collection<String> selectionArgs = new ArrayList<String>();
	}

	public ProjectionMap buildProjectionMap() {
		ProjectionMap.Builder builder = new ProjectionMap.Builder();
		Collection<ContractDesc> contractDescs = mTables.values();
		for (ContractDesc contractDesc : contractDescs) {
			builder.addAll(contractDesc.buildProjectionMap());
		}
		return builder.build();
	}

	public void addJoins(String baseTable, StringBuilder sb, String[] projection) {
		Set<String> tables = new HashSet<String>();
		for (String field : projection) {
			if (BaseColumns._ID.equals(field))
				continue;
			String string = mFields.get(field);
			if (string == null)
				throw new IllegalArgumentException("Could not found field "
						+ field + "in contract");
			tables.add(string);
		}
		tables.remove(baseTable);

		for (String table : tables) {
			ConnectionDesc desc = findConnection(table, baseTable);
			if (desc == null)
				throw new IllegalArgumentException(
						"Wrong where paramters could not connect " + table
								+ " to table: " + baseTable);
			sb.append(" LEFT OUTER JOIN ");
			sb.append(desc.table1);
			sb.append(" ON (");
			sb.append(desc.field1);
			sb.append(" == ");
			sb.append(desc.fieldN);
			sb.append(")");
		}
	}

	public SelectionVars getSelectionVarsFromUri(Uri uri) {
		SelectionVars vars = new SelectionVars();
		StringBuilder where = new StringBuilder();
		List<String> pathSegments = uri.getPathSegments();
		int currentPathSegment = pathSegments.size() - 1;

		if (pathSegments.size() == 0)
			throw new IllegalArgumentException("Unknown URI " + uri);
		int toClose = 0;

		if (lastSegmentIsId(pathSegments)) {
			String id = pathSegments.get(currentPathSegment--);
			vars.table = pathSegments.get(currentPathSegment--);
			ContractDesc contractDesc = mTables.get(vars.table);
			if (contractDesc == null)
				throw new IllegalArgumentException("Unknown URI " + uri
						+ " not known table: " + vars.table);
			toClose += 1;
			where.append("((");
			where.append(contractDesc.getIdField());
			where.append(" == ? )");

			vars.selectionArgs.add(id);
		} else {
			vars.table = pathSegments.get(currentPathSegment--);
			ContractDesc contractDesc = mTables.get(vars.table);
			if (contractDesc == null)
				throw new IllegalArgumentException("Unknown URI " + uri
						+ " not known table: " + vars.table);
		}

		String tableN = vars.table;
		while (currentPathSegment >= 0) {
			String id = pathSegments.get(currentPathSegment--);
			String table1 = pathSegments.get(currentPathSegment--);
			ContractDesc contractDesc = mTables.get(table1);
			if (contractDesc == null)
				throw new IllegalArgumentException("Unknown URI " + uri
						+ " not known table: " + table1);

			ConnectionDesc connection = findConnection(table1, tableN);
			if (connection == null)
				throw new IllegalArgumentException("Unknown URI " + uri
						+ " could not connect" + tableN + " with table "
						+ table1);

			if (tableN == vars.table)
				where.append(" && ");
			where.append(connection.fieldN);
			where.append(" IN ( SELECT ");
			where.append(connection.field1);
			where.append(" FROM ");
			where.append(connection.table1);
			where.append(" WHERE (");
			where.append(contractDesc.getIdField());
			where.append(" = ? )");
			toClose += 1;
			vars.selectionArgs.add(id);

			tableN = table1;
		}
		while (toClose > 0) {
			toClose--;
			where.append(")");
		}
		vars.selection = where.toString();
		if (TextUtils.isEmpty(where))
			vars.selection = null;
		if (BuildConfig.DEBUG)
			Log.v(TAG, String.format("Selection: %s", vars.selection));
		return vars;
	}

	private ConnectionDesc findConnection(String table1, String tableN) {
		for (ConnectionDesc connection : mConnectionDescs) {
			if (!connection.table1.equals(table1)
					|| !connection.tableN.equals(tableN))
				continue;
			return connection;
		}
		return null;
	}

	private static boolean lastSegmentIsId(List<String> pathSegments) {
		return pathSegments.size() % 2 == 0;
	}
}
