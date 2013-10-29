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

package org.fao.geonet.services.dataprep.importdataset;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.security.Principal;
import java.sql.SQLException;
import java.util.ArrayList;

import javax.print.PrintService;

import jeeves.constants.Jeeves;
import jeeves.interfaces.Service;
import jeeves.resources.dbms.Dbms;
import jeeves.server.ServiceConfig;
import jeeves.server.context.ServiceContext;
import jeeves.utils.Util;
import jxl.Cell;
import jxl.Sheet;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.fao.geonet.GeonetContext;
import org.fao.geonet.aurin.util.excel.ReadExcel;
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

public class ImportExcel implements Service {
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

		DataManager dataMan = gc.getDataManager();

		JSONObject responseJSONObj = new JSONObject();
		if (cmd != null && "uploadExcelFileAndGetSheetName".equals(cmd)) {
			String excelfilePath = Util.getParam(params, "excelfile");
			JSONArray sheetInfoJSONArray = ReadExcel
					.getSheetNames(excelfilePath);
			context.getUserSession().setProperty("uploaded_excelfile",
					excelfilePath);
			context.getUserSession().setProperty("uploaded_file",
					new File(excelfilePath));
			responseJSONObj.put("sheetsInfo", sheetInfoJSONArray);
			responseJSONObj.put("success", true);

		} else if (cmd != null && "import_selectedsheets_todb".equals(cmd)) {
			String dbInfo = Util.getParam(params, "dbInfo");
			String selectedsheetsInfo = Util.getParam(params,
					"selectedsheetsInfo");
			JSONObject dbInfoJSONObj = new JSONObject(dbInfo);
			JSONArray selectedsheetsInfoJSONArray = new JSONArray(
					selectedsheetsInfo);
			String excelfilePath = null;
			try {
				excelfilePath = context.getUserSession()
						.getProperty("uploaded_excelfile").toString();
			} catch (Exception e) {
				e.printStackTrace();
				throw new Exception(
						"Session Expired, Refresh Page and Upload Excel File Again");

			}

			Sheet[] sheets = ReadExcel.getSheets(excelfilePath);
			JSONArray sheetsimport_resultJSONArray = new JSONArray();
			for (int i = 0; i < selectedsheetsInfoJSONArray.length(); i++) {
				JSONObject selectedsheet = (JSONObject) selectedsheetsInfoJSONArray
						.get(i);
				Sheet candidateSheet = null;
				for (int j = 0; j < sheets.length; j++) {
					if (sheets[j].getName().equals(
							selectedsheet.getString("name"))) {
						candidateSheet = sheets[j];
						break;
					}
				}
				try {

					if (candidateSheet == null)
						throw new Exception(
								"selected sheet not found in upload excel, maybe it is better to start from first step (upload excel again)");
					JSONObject sheetimport_resultJSONObj = importExcelToDB(
							dbInfoJSONObj,
							selectedsheet.getString("tablename"),
							candidateSheet);
					sheetimport_resultJSONObj.put("name",
							selectedsheet.getString("name"));
					sheetimport_resultJSONObj.put("tablename",
							selectedsheet.getString("tablename"));
					sheetimport_resultJSONObj.put("success",true);
					sheetsimport_resultJSONArray.put(sheetimport_resultJSONObj);
				} catch (Exception e) {
					JSONObject sheetimport_resultJSONObj = new JSONObject();
					sheetimport_resultJSONObj.put("name",
							selectedsheet.getString("name"));
					sheetimport_resultJSONObj.put("tablename",
							selectedsheet.getString("tablename"));
					sheetimport_resultJSONObj.put("exception",e.getMessage());
					sheetimport_resultJSONObj.put("success",false);
					sheetsimport_resultJSONArray.put(sheetimport_resultJSONObj);
					e.printStackTrace();
				}
			}
			responseJSONObj.put("sheetsimport_result",
					sheetsimport_resultJSONArray);
			responseJSONObj.put("success", true);

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
			jsonElement.setText(responseJSONObj.toString());
			return jsonElement;
		} else
			return root;
	}

	private JSONObject importExcelToDB(JSONObject dbInfoJSONObj,
			String tableName, Sheet sheet) throws ClassNotFoundException,
			JSONException, SQLException, Exception, IOException {
		Dbms dbms = null;
		JSONObject sheetimport_resultJSONObj = new JSONObject();
		try {
			dbms = new Dbms("org.postgresql.Driver", String.format(
					"jdbc:postgresql://%s:%s/%s",
					dbInfoJSONObj.getString("ip"),
					dbInfoJSONObj.getString("port"),
					dbInfoJSONObj.getString("database_name")));
			dbms.connect(dbInfoJSONObj.getString("username"),
					dbInfoJSONObj.getString("password"));

			// Statement statement = candidatedb_conn.createStatement();
			try {
				dbms.execute(String.format(
						"CREATE TABLE \"%s\"  ()  WITH (OIDS = FALSE)",
						tableName));
				dbms.execute(String.format(
						"ALTER TABLE  \"%s\"  OWNER TO postgres", tableName));
				dbms.execute(String.format(
						"GRANT ALL ON TABLE \"%s\" TO public;", tableName));

			} catch (Exception e) {
				throw e;

			}

			Cell[] columnHeaders = sheet.getRow(0);
			String commaDelimited_columnNames = "";
			ArrayList<String> columnNameArray = new ArrayList<String>();
			for (int i = 0; i < columnHeaders.length; i++) {

				try {
					String columnname = columnHeaders[i].getContents();
					if (StringUtils.isEmpty(columnname)){
							columnname = "Column_"+i;
							sheetimport_resultJSONObj.put(columnname+"_Issue",
									"Column name is empty, replaced with \""+columnname+"\"");
					}
					columnname = columnname.replaceAll(" ", "_");
					if (columnNameArray.contains(columnname)){
						columnname += "_duplicate";
					}
					columnNameArray.add(columnname);
					dbms.execute(String
							.format("	ALTER TABLE  \"%s\" ADD COLUMN  \"%s\" character varying;",
									tableName,columnname ));
					commaDelimited_columnNames += "\""
							+ columnname + "\"";
					if (i != columnHeaders.length - 1)
						commaDelimited_columnNames += " , ";

				} catch (Exception e) {
						e.printStackTrace();
						throw e;

				}
			}
			dbms.commit();
			int successfull_import_count = 0;
			int failed_import_count = 0;
			JSONArray failed_import_infos = new JSONArray();
			for (int r = 1; r < sheet.getRows(); r++) {
				String row_values = "";
				for (int c = 0; c < sheet.getColumns(); c++) {
					Cell cell = sheet.getCell(c, r);
					String value = cell.getContents();
				//	value = URLEncoder.encode(value,"UTF-8") ;
					value = value.replaceAll("'", "\\\\'") ;
					
					row_values += "\'" + value+ "\'";
					if (c != (sheet.getColumns() - 1))
						row_values += " , ";
				}
				String query = "";
				try {
					query = String.format(
							"INSERT INTO \"%s\" (%s) VALUES (%s)", tableName,
							commaDelimited_columnNames, row_values);
					dbms.execute(query);
					if (r%10==0)
						dbms.commit();
					successfull_import_count++;
				} catch (Exception e) {
					failed_import_count++;
					JSONObject failed_import_info = new JSONObject();
					failed_import_info.put("executed_query", query);
					failed_import_info.put("exception",e.getMessage());
					failed_import_infos.put(failed_import_info);
					e.printStackTrace();
					dbms.commit();
					

				}
			}
			sheetimport_resultJSONObj.put("successfull_import_count ",
					successfull_import_count);
			sheetimport_resultJSONObj.put("failed_import_count ",
					failed_import_count);
			sheetimport_resultJSONObj.put("failed_import_infos ",
					failed_import_infos);

		}  catch (Exception e) {
			throw new Exception(e);
		}
		finally {
			if (dbms != null&&dbms.getConnection()!=null) {
				dbms.commit();
				dbms.disconnect();
			}
		}
		return sheetimport_resultJSONObj;
	}

}
