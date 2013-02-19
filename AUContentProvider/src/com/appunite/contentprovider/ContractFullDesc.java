package com.appunite.contentprovider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;

public class ContractFullDesc {
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

	public static class ConnectionDesc {
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
		private Set<String> mTablesNames;
		private Set<String> mFields;

		public Builder(String authority) {
			mDesc = new ContractFullDesc();
			mDesc.mAuthority = authority;
			mTablesNames = new HashSet<String>();
			mFields = new HashSet<String>();
		}

		private void checkIfTableNameExistAndAdd(String tableName) {
			if (mTablesNames.contains(tableName)) {
				throw new IllegalArgumentException(String.format(
						"Table %s already exist in contract", tableName));
			}
			mTablesNames.add(tableName);
		}
		
		private void checkIfFieldExistAndAdd(String fieldName) {
			if (mFields.contains(fieldName)) {
				throw new IllegalArgumentException(String.format(
						"Field %s already exist in some table", fieldName));
			}
			mFields.add(fieldName);
		}
		
		private void checkIfFieldsExistAndAdd(Collection<String> fields) {
			for (String fieldName : fields) {
				checkIfFieldExistAndAdd(fieldName);
			}
		}

		public Builder addTable(ContractDesc table) {
			checkIfTableNameExistAndAdd(table.getTableName());
			checkIfFieldsExistAndAdd(table.getFieldsWithId());
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
		public String insertField;
		public String insertValue;
	}

	public ProjectionMap buildProjectionMap() {
		ProjectionMap.Builder builder = new ProjectionMap.Builder();
		Collection<ContractDesc> contractDescs = mTables.values();
		for (ContractDesc contractDesc : contractDescs) {
			builder.addAll(contractDesc.buildProjectionMap());
		}
		return builder.build();
	}

	/**
	 * Return All joins that have to be processed between two or more tables
	 * even if there are no direct connection.
	 * 
	 * If there are model: books.book_shop_id -> book_shop.id,
	 * book_shop.book_store_id -> book_store.id. You ask for: book, book_sotre.
	 * Return: book, book_shop, book_store - because there no dirrect connection
	 * between book and book_store
	 * 
	 * @param toFind
	 *            tables to find destination
	 * @param baseTable
	 *            base table
	 * @return set of connections to perform tables to find join
	 */
	private List<ConnectionDesc> buildJoinArrayFromSet(Set<String> toFind,
			String baseTable) {
		Map<String, List<ConnectionDesc>> root = new HashMap<String, List<ConnectionDesc>>();
		root.put(baseTable, null);

		List<ConnectionDesc> ret = new ArrayList<ConnectionDesc>();
		Set<ConnectionDesc> retSet = new HashSet<ConnectionDesc>();
		Set<String> visited = new HashSet<String>();

		while (root.size() > 0 && toFind.size() > 0) {
			Map<String, List<ConnectionDesc>> newRoot = new HashMap<String, List<ConnectionDesc>>();
			Iterator<ConnectionDesc> connectionDescsIterator = mConnectionDescs
					.iterator();
			while (connectionDescsIterator.hasNext() && toFind.size() > 0) {
				ConnectionDesc connection = connectionDescsIterator.next();
				if (!root.containsKey(connection.tableN))
					continue;
				if (visited.contains(connection.table1)) {
					continue;
				}
				List<ConnectionDesc> descTree = root.get(connection.tableN);
				visited.add(connection.table1);
				if (toFind.contains(connection.table1)) {
					toFind.remove(connection.table1);
					if (descTree != null) {
						for (ConnectionDesc connectionDesc : descTree) {
							if (retSet.contains(connectionDesc))
								continue;
							ret.add(connectionDesc);
							retSet.add(connectionDesc);
						}
					}
					if (!retSet.contains(connection)) {
						ret.add(connection);
						retSet.add(connection);
					}
					newRoot.put(connection.table1, null);
				} else {
					ArrayList<ConnectionDesc> newDescTree = new ArrayList<ConnectionDesc>();
					if (descTree != null)
						newDescTree.addAll(descTree);
					newDescTree.add(connection);
					newRoot.put(connection.table1, newDescTree);
				}
			}
			root = newRoot;
		}
		if (toFind.size() != 0)
			throw new RuntimeException("Could not find connection between 1:N"
					+ toFind + ":" + baseTable);
		return ret;
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

		List<ConnectionDesc> tablesJoin = buildJoinArrayFromSet(tables,
				baseTable);

		for (ConnectionDesc desc : tablesJoin) {
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
		vars.insertField = null;
		vars.insertValue = null;
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
		if (currentPathSegment >= 0) {
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

			if (toClose > 0) {
				where.append(" AND ");
			}
			
			if (contractDesc.getIdField().equals(connection.field1)) {
				// if field that we using for join is same as "id" we do not
				// have to ask database for value to create join
				where.append("(");
				where.append(connection.fieldN);
				where.append(" == ? ");
				vars.insertField = connection.fieldN;
				vars.insertValue = id;
			} else {
				where.append(connection.fieldN);
				where.append(" IN ( SELECT ");
				where.append(connection.field1);
				where.append(" FROM ");
				where.append(connection.table1);
				where.append(" WHERE (");
				where.append(contractDesc.getIdField());
				where.append(" = ? )");
			}
			vars.selectionArgs.add(id);
			toClose += 1;
			tableN = table1;
		}
		for (;toClose > 0; toClose--) {
			where.append(")");
		}
		vars.selection = where.toString();
		if (TextUtils.isEmpty(where))
			vars.selection = null;
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

	public String getTypeURI(Uri uri) {
		List<String> pathSegments = uri.getPathSegments();
		
		if (pathSegments.size() == 0)
			throw new IllegalArgumentException("Unknown URI " + uri);
		
		boolean isLastSegmentId = lastSegmentIsId(pathSegments);
		
		int currentPathSegment = isLastSegmentId ? pathSegments.size() - 2
				: pathSegments.size() - 1;

		String table = pathSegments.get(currentPathSegment);
		ContractDesc contractDesc = mTables.get(table);
		
		if (contractDesc == null)
			throw new IllegalArgumentException("Unknown URI " + uri
					+ " not known table: " + table);

		return isLastSegmentId ? contractDesc.getContentItemType()
				: contractDesc.getContentType();

	}
	
	private static class TableField {
		final String table;
		final String field;
		
		public TableField(String table, String field) {
			this.table = table;
			this.field = field;
			if (table == null) {
				throw new NullPointerException();
			}
			if (field == null) {
				throw new NullPointerException();
			}
		}
		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof TableField))
				return false;
			TableField obj = (TableField) o;
			if (table == null ? obj.table != null : !table.equals(obj.table)) {
				return false;
			}
			if (field == null ? obj.field != null : !field.equals(obj.field)) {
				return false;
			}
			return true;
		}
	}
	
	public void sqlCreateAll(SQLiteDatabase db) {
		for (ContractDesc contractDesc : mTables.values()) {
			contractDesc.sqlCreateTable(db);
		}
		Set<TableField> fieldSet = new HashSet<TableField>();
		for (ConnectionDesc connectionDesc : mConnectionDescs) {
			String tableN = connectionDesc.tableN;
			String fieldN = connectionDesc.fieldN;
			fieldSet.add(new TableField(tableN, fieldN));
		}
		for (TableField tableField : fieldSet) {
			ContractDesc contractDesc = mTables.get(tableField.table);
			if (contractDesc.mIsFts) {
				continue;
			}
			if (contractDesc.getIdField().equals(tableField.field)) {
				continue;
			}
			String guidField = contractDesc.getGuidField();
			if (guidField != null && guidField.equals(tableField.field)) {
				continue;
			}
			DataHelper.createBinaryUniqueIndexIfNotExist(tableField.table,
					tableField.field);
		}
	}
	
	public void sqlDropAll(SQLiteDatabase db) {
		for (ContractDesc contractDesc : mTables.values()) {
			contractDesc.sqlDropTable(db);
		}
	}
}
