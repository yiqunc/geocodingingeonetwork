//=============================================================================
//===	Copyright (C) 2001-2007 Food and Agriculture Organization of the
//===	United Nations (FAO-UN), United Nations World Food Programme (WFP)
//===	and United Nations Environment Programme (UNEP)
//===
//===	This program is free software; you can redistribute it and/or modify
//===	it under the terms of the GNU General Public License as published by
//===	the Free Software Foundation; either version 2 of the License, or (at
//===	your option) any later version.
//===
//===	This program is distributed in the hope that it will be useful, but
//===	WITHOUT ANY WARRANTY; without even the implied warranty of
//===	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
//===	General Public License for more details.
//===
//===	You should have received a copy of the GNU General Public License
//===	along with this program; if not, write to the Free Software
//===	Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301, USA
//===
//===	Contact: Jeroen Ticheler - FAO - Viale delle Terme di Caracalla 2,
//===	Rome - Italy. email: geonetwork@osgeo.org
//==============================================================================

package org.fao.geonet.services.dataprep;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.StringTokenizer;

import jeeves.constants.Jeeves;
import jeeves.interfaces.Service;
import jeeves.server.ServiceConfig;
import jeeves.server.context.ServiceContext;
import jeeves.utils.Util;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.fao.geonet.GeonetContext;
import org.fao.geonet.constants.Geonet;
import org.fao.geonet.kernel.DataManager;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.postgresql.util.PSQLException;

//=============================================================================

/**
 * Inserts a new metadata to the system (data is validated)
 */

public class ChangeColumnType implements Service {
	// --------------------------------------------------------------------------
	// ---
	// --- Init
	// ---
	// --------------------------------------------------------------------------

	private String stylePath;
	/**
	 * Indicates whether the service is returning output in JSON format or other
	 * formats(e.g. XML) It should be set in service definition files
	 * (config*.xml like config-aurin.xml)
	 */
	private Boolean jsonOutput = false;
	private String cmd;

	public void init(String appPath, ServiceConfig params) throws Exception {
		this.stylePath = appPath + Geonet.Path.IMPORT_STYLESHEETS;
		this.jsonOutput = BooleanUtils.toBooleanObject(params
				.getValue("jsonOutput"));
		this.cmd = params.getValue("cmd");
	}

	// --------------------------------------------------------------------------
	// ---
	// --- Service
	// ---
	// --------------------------------------------------------------------------

	public Element exec(Element params, ServiceContext context)
			throws Exception {
		GeonetContext gc = (GeonetContext) context
				.getHandlerContext(Geonet.CONTEXT_NAME);
		// context.getUserSession().
		DataManager dataMan = gc.getDataManager();

		// Return response
		// Element results = new Element("results");
		JSONObject responseJSONObj = new JSONObject();
		if (cmd != null && "change_type".equals(cmd)) {
			String dbInfo = Util.getParam(params, "dbInfo");
			String tableName = Util.getParam(params, "tableName");
			String selectedcolumns = Util.getParam(params, "selectedcolumns");
			String target_type = Util.getParam(params, "target_type");
			JSONArray selectedcolumnsJSONArray = new JSONArray(selectedcolumns);
			JSONObject dbInfoJSONObj = new JSONObject(dbInfo);
			Connection candidatedb_conn = jeeves.util.jdbc.DirectJDBCConnection
					.getNewConnection(dbInfoJSONObj.getString("ip"),
							dbInfoJSONObj.getString("port"),
							dbInfoJSONObj.getString("database_name"),
							dbInfoJSONObj.getString("username"),
							dbInfoJSONObj.getString("password"));
			Integer success_geocode_count = 0;
			Integer failure_geocode_count = 0;
			JSONArray failureRecords = new JSONArray();
			JSONArray successRecords = new JSONArray();
			Statement stmt = null;
			String integer_rg_exp = "^\\\\d+$";
			String double_rg_exp = "^\\\\d+\\\\.\\\\d+$";
			String negative_double_rg_exp = "^\\\\-\\\\d+\\\\.\\\\d+$";
			try {
				stmt = candidatedb_conn.createStatement();
				int size = selectedcolumnsJSONArray.length();
				for (int i = 0; i < size; i++) {
					JSONObject columnJSONObj = (JSONObject) selectedcolumnsJSONArray
							.get(i);
					String columnName = columnJSONObj.getString("name");
					//
					ResultSet anomalyRS = null;
					if ("Integer".equals(target_type)) {
						anomalyRS = stmt
								.executeQuery(String
										.format("select t.\"%s\"  from \"%s\" t where  t.\"%s\"::text !~ '%s'   limit 1 ",
												columnName, tableName,
												columnName, integer_rg_exp));
					} else if ("Double".equals(target_type)) {
						anomalyRS = stmt
								.executeQuery(String
										.format("select t.\"%s\"  from \"%s\" t where  ( (t.\"%s\"::text !~ '%s') and (t.\"%s\"::text !~ '%s')   and (t.\"%s\"::text !~ '%s') )    limit 1 ",
												columnName, tableName,
												columnName, integer_rg_exp,
												columnName, double_rg_exp,
												columnName, negative_double_rg_exp)
												);
					}

					if (anomalyRS.next()) {
						String anomalyValue = anomalyRS.getString(columnName);
						failure_geocode_count++;
						JSONObject _jsonObject = new JSONObject();
						_jsonObject.put("columnName", columnName);
						_jsonObject.put("failureReason", "column is not "
								+ target_type + " (value:" + anomalyValue
								+ ") ");
						failureRecords.put(_jsonObject);
					} else {
						try {
							if ("Integer".equals(target_type)) {
								stmt.executeUpdate(String
										.format("ALTER TABLE \"%s\" ALTER COLUMN \"%s\" TYPE integer USING \"%s\"::INTEGER;",
												tableName, columnName,
												columnName));
								JSONObject _jsonObject = new JSONObject();
								_jsonObject.put("columnName", columnName);
								successRecords.put(_jsonObject);
								success_geocode_count++;
							} else if ("Double".equals(target_type)) {
								ResultSet anyDoubleRS = stmt
										.executeQuery(String
												.format("select t.\"%s\"  from \"%s\" t where   ( t.\"%s\"::text ~ '%s' or t.\"%s\"::text ~ '%s' )  limit 1 ",
														columnName, tableName,
														columnName,double_rg_exp,
														columnName,negative_double_rg_exp
														));
								if (anyDoubleRS.next()) {
									stmt.executeUpdate(String
											.format("ALTER TABLE \"%s\" ALTER COLUMN \"%s\" TYPE double precision USING \"%s\"::double precision;",
													tableName, columnName,
													columnName));
									JSONObject _jsonObject = new JSONObject();
									_jsonObject.put("columnName", columnName);
									successRecords.put(_jsonObject);
									success_geocode_count++;
								} else {
									failure_geocode_count++;
									JSONObject _jsonObject = new JSONObject();
									_jsonObject.put("columnName", columnName);
									_jsonObject
											.put("failureReason",
													"no double value found in this column to set its type to double");
									failureRecords.put(_jsonObject);
								}

							}
						} catch (Exception e) {
							e.printStackTrace();
							failure_geocode_count++;
							JSONObject _jsonObject = new JSONObject();
							_jsonObject.put("columnName", columnName);
							_jsonObject
									.put("Exception Message", e.getMessage());
							failureRecords.put(_jsonObject);

						}
					}
					System.out.println(i+" out of "+size+", Column change processing wad done, column name:"+columnName);
				}
			} finally {
				if (stmt != null && stmt.getConnection() != null)
					stmt.close();
			}
			responseJSONObj.put("success_count", success_geocode_count);
			responseJSONObj.put("failure_count", failure_geocode_count);
			responseJSONObj.put("success_records", successRecords);
			responseJSONObj.put("failure_records", failureRecords);

		}

		Element root = new Element(Jeeves.Elem.ROOT);
		// Element success = new Element("success");
		// success.setText("OK");
		// root.addContent(results);
		// root.addContent(success);

		if (BooleanUtils.isTrue(this.jsonOutput)) {
			XMLOutputter xx = new XMLOutputter();
			// String jsonContent = org.json.XML.toJSONObject(
			// xx.outputString(root)).toString();
			Element jsonElement = new Element("jsonElement");
			jsonElement.setText(responseJSONObj.toString(10));
			return jsonElement;
		} else
			return root;
	}

}
