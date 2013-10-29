package org.fao.geonet.aurin.util.excel;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import jxl.Cell;
import jxl.CellType;
import jxl.Sheet;
import jxl.Workbook;
import jxl.read.biff.BiffException;

public class ReadExcel {

	private String inputFile;

	public void setInputFile(String inputFile) {
		this.inputFile = inputFile;
	}

	public void read() throws IOException {
		File inputWorkbook = new File(inputFile);
		Workbook w;
		try {
			w = Workbook.getWorkbook(inputWorkbook);
			// Get the first sheet
			Sheet sheet = w.getSheet(0);
			// Loop over first 10 column and lines

			for (int j = 0; j < sheet.getColumns(); j++) {
				for (int i = 0; i < sheet.getRows(); i++) {
					Cell cell = sheet.getCell(j, i);
					CellType type = cell.getType();
					if (cell.getType() == CellType.LABEL) {
						System.out.println("I got a label "
								+ cell.getContents());
					}

					if (cell.getType() == CellType.NUMBER) {
						System.out.println("I got a number "
								+ cell.getContents());
					}

				}
			}
		} catch (BiffException e) {
			e.printStackTrace();
		}
	}

	public static JSONArray getSheetNames(String localPath) throws IOException {
		File inputFile = new File(localPath);
		
		JSONArray jsonArray = new JSONArray();
		try {
			Workbook w = Workbook.getWorkbook(inputFile);
			Sheet[] sheets =  w.getSheets();
			for (int i = 0; i < sheets.length; i++) {
				JSONObject sheetJSONObject = new JSONObject();
				Sheet sheet = sheets[i];				
				try {
					sheetJSONObject.put("name", sheet.getName());
//					sheetJSONObject.put("columns", sheet.getRow(0));
					sheetJSONObject.put("rowcount", sheet.getRows());
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				jsonArray.put(sheetJSONObject );
			}
			return jsonArray;
		} catch (BiffException e) {
			e.printStackTrace();
		}
		return null;
	}
	public static Sheet[] getSheets(String localPath) throws IOException {
		File inputFile = new File(localPath);
		try {
			Workbook w = Workbook.getWorkbook(inputFile);
			Sheet[] sheets =  w.getSheets();
			return sheets;
		} catch (BiffException e) {
			e.printStackTrace();
		}
		return null;
	}
	

}