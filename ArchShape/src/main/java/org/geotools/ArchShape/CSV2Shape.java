package org.geotools.ArchShape;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import org.apache.commons.lang3.StringUtils;

import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.swing.data.JFileDataStoreChooser;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.geometry.DirectPosition;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;
import net.sf.geographiclib.GeodesicLine;
import net.sf.geographiclib.GeodesicMask;

/**
 * This class reads a datum coordinate and relational location tags (following the format N1E1) from a comma separated values (CSV) file
 * 	 and converts them into true coordinates based on their relationship to the datum coordinate.
 * It writes these coordinates, location tags, and descriptions to a shapefile
 * (CSV) file and exports them as a new shapefile. It illustrates how to build a feature type.
 */
public class CSV2Shape {
	
	private static org.locationtech.jts.geom.Coordinate siteDatum; //the datum point of the site, which all other points are defined in relation to
	private static final double conversion = 111319.5; //the conversion of coordinate degrees to meters

	/**
	 * Open a GUI window to allow the user to input a csv files containing their datum coordinate
	 */
    public static void main(String[] args) throws Exception {

        File file = JFileDataStoreChooser.showOpenFile("csv", null);
        if (file == null) {
            return;
        }
        
        /*
         * Use the DataUtilities class to create a FeatureType that will describe the data in the shapefile.
         * In this case, the shapefile will include a coordinate, a description ("point type") and a location tag ("excCoord").
         */
        final SimpleFeatureType COORD = DataUtilities.createType("Location",
                "the_geom:Point:srid=4326," + // <- the geometry attribute: Point type
        		"point type:String," +
                "excCoord:String"
        );
        
        /*
         * Use the DataUtilities class to create a FeatureType that will describe the data in the shapefile.
         * In this case, the shapefile will include a line, a description ("line type") and a location tag ("excCoord").
         */
        final SimpleFeatureType LINE = DataUtilities.createType("Line", 
        		"the_geom:LineString:srid=32615," + 
        		"line type:String," + 
        		"excCoord:String"
        );
        
        /*
         * Use the DataUtilities class to create a FeatureType that will describe the data in the shapefile.
         * In this case, the shapefile will include a polygon, a description ("type") and a location tag ("excCoord").
         */
        final SimpleFeatureType POLYGON = DataUtilities.createType("Polygon", 
        		"the_geom:Polygon:srid=32615," + 
        		"type:String," + 
        		"excCoord:String"
        );
        
        /*
         * We create a FeatureCollection into which we will put each Feature created from a record
         * in the input csv data file
         * One collection for each type of shapefile feature. All features of that type will be stored in this FeatureCollection
         */
        DefaultFeatureCollection coordCollection = new DefaultFeatureCollection();
        DefaultFeatureCollection lineCollection = new DefaultFeatureCollection();
        DefaultFeatureCollection polygonCollection = new DefaultFeatureCollection();
        
        /*
         * GeometryFactory will be used to create the geometry attribute of each feature (a Point
         * object for the location, or a line object, or a polygon)
         */
        org.locationtech.jts.geom.GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory(null);

        /*
         * These objects give the program instructions to build the feature, based on the SimpleFeatureType from above
         */
        SimpleFeatureBuilder coordFeatureBuilder = new SimpleFeatureBuilder(COORD);
        SimpleFeatureBuilder lineFeatureBuilder = new SimpleFeatureBuilder(LINE);
        SimpleFeatureBuilder polygonFeatureBuilder = new SimpleFeatureBuilder(POLYGON);

        //To read in the file
        BufferedReader reader = new BufferedReader(new FileReader(file));
        
        //Retrieve datum coordinate values
        try {
            /* First line of the data file is the header */
            String line = reader.readLine();
            System.out.println("Header: " + line);

            //Loop through all lines
            for (line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.trim().length() > 0) { // skip blank lines
                    String tokens[] = line.split("\\,");

                    //Assumes table contains three columns: latitude, longitude, and label
                    //TODO: make this more flexible
                    double latitude = Double.parseDouble(tokens[0].replaceAll("\"", ""));
                    double longitude = Double.parseDouble(tokens[1].replaceAll("\"", ""));
                    String label = tokens[2].replaceAll("\"", "");
                    
                    /* Longitude (= x coord) first ! */
                    //Retrieve datum coordinate, the only one in this file
                    siteDatum = new org.locationtech.jts.geom.Coordinate(longitude, latitude);
                    org.locationtech.jts.geom.Point point = geometryFactory.createPoint(siteDatum);

                    //Save datum coordinate to the coordinates collection
                    coordCollection.add(addCoordinate(coordFeatureBuilder, point, "N0E0", label));
                }
            }
            
        } finally {
            reader.close();
        }
        
        
        //Now, open another GUI window to allow the user to input file containing relational location tags
        file = JFileDataStoreChooser.showOpenFile("csv", null);
        
        //if file is empty, stop
        if (file == null) {
            return;
        }
        
        //read file
        reader = new BufferedReader(new FileReader(file));
        try {
            /* First line of the data file is the header */
            String line = reader.readLine();
            System.out.println("Header: " + line);

            //read all lines
            for (line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.trim().length() > 0) { // skip blank lines
                    String tokens[] = line.split("\\,");

                    //Assumes that table contains two columns: a relational location tag and a description of the object found there
                    //TODO: Make this more flexible
                    String excCoord = tokens[1];
                    String excObj = tokens[2];
                    
                    //Pass the builders, collections, and coordinate information to this handler method to determine if the "coordinate"
                    //	is a coordinate, a line, or a polygon and handle it accordingly
                    handleCoordinateLinePolygon(geometryFactory, coordCollection, 
                    		coordFeatureBuilder, lineCollection, lineFeatureBuilder, polygonCollection, polygonFeatureBuilder, 
                    		excCoord, excObj);
                }
            }
            
        } finally {
            reader.close();
        }
        
        /*
         * Create the new shapefiles--one for each type
         */
        createShapeFile(file, COORD, coordCollection);
        createShapeFile(file, LINE, lineCollection);
        createShapeFile(file, POLYGON, polygonCollection);

        System.exit(0); // success!
    }
    
    /**
     * Prompt the user for the name and path to use for the output shapefile
     * 
     * @param csvFile
     *            the input csv file used to create a default shapefile name
     * 
     * @return name and path for the shapefile as a new File object
     */
    private static File getNewShapeFile(File csvFile, String type) {
    	
    	//build a shapefile in the same file as the input files
        String path = csvFile.getAbsolutePath();
        String newPath = path.substring(0, path.length() - 4) + type + ".shp";

        //let the user choose the final path
        JFileDataStoreChooser chooser = new JFileDataStoreChooser("shp");
        chooser.setDialogTitle("Save shapefile");
        chooser.setSelectedFile(new File(newPath));

        int returnVal = chooser.showSaveDialog(null);

        if (returnVal != JFileDataStoreChooser.APPROVE_OPTION) {
            // the user cancelled the dialog
            System.exit(0);
        }

        File newFile = chooser.getSelectedFile();
        if (newFile.equals(csvFile)) {
            System.out.println("Error: cannot replace " + csvFile);
            System.exit(0);
        }

        return newFile;
    }
    
    /**
     * Create a shapefile at file, following the specified schema, populated by this collection
     * @param file
     * @param schema
     * @param collection
     */
    private static void createShapeFile(File file, SimpleFeatureType schema, DefaultFeatureCollection collection) {
    	try {
	    	File newFile = getNewShapeFile(file, schema.getTypeName());
	
	        ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();
	
	        Map<String, Serializable> params = new HashMap<String, Serializable>();
	        params.put("url", newFile.toURI().toURL());
	        params.put("create spatial index", Boolean.TRUE);
	
	        ShapefileDataStore newDataStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(params);
	        newDataStore.createSchema(schema);
	
	        /*
	         * You can comment out this line if you are using the createFeatureType method (at end of
	         * class file) rather than DataUtilities.createType
	         */
	        newDataStore.forceSchemaCRS(DefaultGeographicCRS.WGS84);
	        
	        /*
	         * Write the features to the shapefile
	         */
	        Transaction transaction = new DefaultTransaction("create");
	
	        String typeName = newDataStore.getTypeNames()[0];
	        SimpleFeatureSource featureSource = newDataStore.getFeatureSource(typeName);
	
	        if (featureSource instanceof SimpleFeatureStore) {
	            SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
	
	            featureStore.setTransaction(transaction);
	            try {
	                featureStore.addFeatures(collection);
	                transaction.commit();
	
	            } catch (Exception problem) {
	                problem.printStackTrace();
	                transaction.rollback();
	
	            } finally {
	                transaction.close();
	            }
	        } else {
	            System.out.println(typeName + " does not support read/write access");
	            System.exit(1);
	        }
    	} catch (IOException exception) {
    		exception.printStackTrace();
    	}
    }
    
    /**
     * A handler method to add a point to the specified FeatureBuilder
     * TODO: see if this can be combined with addLine and addPolygon
     * @param ftBuild
     * @param pt
     * @param coord
     * @param lbl
     * @return
     */
    private static SimpleFeature addCoordinate(SimpleFeatureBuilder ftBuild, org.locationtech.jts.geom.Point pt, String coord, String lbl) {

        ftBuild.add(pt);
        ftBuild.add(lbl);
        ftBuild.add(coord); 
        return ftBuild.buildFeature(null);
    }
    
    /**
     * A handler method to add a line to the specified FeatureBuilder
     * TODO: see if this can be combined with addCoordinate and addPolygon
     * @param ftBuild
     * @param ln
     * @param coord
     * @param lbl
     * @return
     */
    private static SimpleFeature addLine(SimpleFeatureBuilder ftBuild, org.locationtech.jts.geom.LineString ln, String coord, String lbl) {

        ftBuild.add(ln);
        ftBuild.add(lbl);
        ftBuild.add(coord); 
        return ftBuild.buildFeature(null);
    }
    
    /**
     * A handler method to add a polygon to the specified FeatureBuilder
     * TODO: see if this can be combined with addCoordinate and addLine
     * @param ftBuild
     * @param pg
     * @param coord
     * @param lbl
     * @return
     */
    private static SimpleFeature addPolygon(SimpleFeatureBuilder ftBuild, org.locationtech.jts.geom.Polygon pg, String coord, String lbl) {

        ftBuild.add(pg);
        ftBuild.add(lbl);
        ftBuild.add(coord); 
        return ftBuild.buildFeature(null);
    }
    
    /**
     * Identifies if a string represents a coordinate (i.e. N2 W4 or 2N 4W), a line (i.e. N2-4 W4 or 2-4N 4W) 
     * or a polygon (i.e. N2-4 W2-4).
     * 
     */
    private static void handleCoordinateLinePolygon(org.locationtech.jts.geom.GeometryFactory geomFact, 
    		DefaultFeatureCollection coordColl,
    		SimpleFeatureBuilder coordinates,
    		DefaultFeatureCollection lineColl,
    		SimpleFeatureBuilder line,
    		DefaultFeatureCollection polygonColl,
    		SimpleFeatureBuilder polygon,
    		String coord,
    		String obj) {
    	
    	if(coord.contains("-")) {
    		//handle as line or polygon
    		if(StringUtils.countMatches(coord, "-") == 2) {
    			//handle as polygon
    			polygonColl.add(addPolygon(polygon, getPolygonFromDatum(geomFact, coord), coord, obj));
    		} else {
    			//handle as line
    			lineColl.add(addLine(line, getLineFromDatum(geomFact, coord), coord, obj));
    		}
    	} else {
    		//handle as coordinate
            //Add coordinate, location tag, and description to the shapefile
    		coordColl.add(addCoordinate(coordinates, geomFact.createPoint(getCoordinateFromDatum(coord)), coord, obj)); //datum and relational points will all be written to one shapefile
    	}
    }
    
    
    /**
     * Converts a relational location tag into a true coordinate using a datum coordinate, bearing, and distance
     * @param datum
     * @return a coordinate representing the location referred to by the relational location tag
     */
    private static org.locationtech.jts.geom.Coordinate getCoordinateFromDatum(String datum) {
    	StringTokenizer parser = new StringTokenizer(datum, "NEWS");
    	
    	//parse location tags into two tokens, each indicating a longitudinal or latitudinal distance
    	double NSCoor = Double.parseDouble(parser.nextToken());
    	double EWCoor = Double.parseDouble(parser.nextToken());
    	
    	//have to account for direction
    	NSCoor = datum.contains("S") || datum.contains("s") ? 0 - NSCoor : NSCoor;
    	EWCoor = datum.contains("W") || datum.contains("w") ? 0 - EWCoor : EWCoor;
    	
    	//increment or decrement the site datum accordingly to calculate the coordinate of this point
    	double endLat =  siteDatum.y + (NSCoor/conversion);
    	double endLong = siteDatum.x + (EWCoor/conversion);
   
    	//return the coordinate
    	return new org.locationtech.jts.geom.Coordinate(endLong, endLat);   	
    }
    
    /**
     * Handler to convert string coordinate into a LineString
     * @param geoFact
     * @param datum
     * @return
     */
    private static org.locationtech.jts.geom.LineString getLineFromDatum(org.locationtech.jts.geom.GeometryFactory geoFact, String datum) {
    	StringTokenizer parser = new StringTokenizer(datum, "NEWS");
    	//parse location tags into two tokens, each indicating a longitudinal or latitudinal distance
    	String ns = parser.nextToken();
    	String ew = parser.nextToken();
    	
    	double NSCoor1 = 0.0;
    	double NSCoor2 = 0.0;
    	double EWCoor1 = 0.0;
    	double EWCoor2 = 0.0;
    	
    	//find which direction includes the range
    	if(ns.contains("-")) {
    		//if n/s has the range, set NSCoor1 and NSCoor2. Then set EWCoor1 and set EWCoor2 equal to it
        	StringTokenizer rangeParser = new StringTokenizer(ns, " - ");    
        	NSCoor1 = Double.parseDouble(rangeParser.nextToken());
        	NSCoor2 = Double.parseDouble(rangeParser.nextToken());
        	EWCoor1 = Double.parseDouble(ew);
        	EWCoor2 = EWCoor1;
    	} else if(ew.contains("-")) {
    		//if e/w has the range, set EWCoor1 and EWCoor2. Then set NSCoor1 and set NSCoor2 equal to it
        	StringTokenizer rangeParser = new StringTokenizer(ew, " - ");   
        	EWCoor1 = Double.parseDouble(rangeParser.nextToken());
        	EWCoor2 = Double.parseDouble(rangeParser.nextToken());
        	NSCoor1 = Double.parseDouble(ns);
        	NSCoor2 = NSCoor1;
    	}
    	
    	//have to account for direction
    	NSCoor1 = datum.contains("S") || datum.contains("s") ? 0 - NSCoor1 : NSCoor1;
    	NSCoor2 = datum.contains("S") || datum.contains("s") ? 0 - NSCoor2 : NSCoor2;
    	EWCoor1 = datum.contains("W") || datum.contains("w") ? 0 - EWCoor1 : EWCoor1;
    	EWCoor2 = datum.contains("W") || datum.contains("w") ? 0 - EWCoor2 : EWCoor2;
    	
    	//increment or decrement the site datum accordingly to calculate the coordinate of these points
    	double startLat =  siteDatum.y + (NSCoor1/conversion);
    	double endLat = siteDatum.y + (NSCoor2/conversion);
    	double startLong = siteDatum.x + (EWCoor1/conversion);
    	double endLong = siteDatum.x + (EWCoor2/conversion);
    	
    	//create start and end coords
    	org.locationtech.jts.geom.Coordinate start = new org.locationtech.jts.geom.Coordinate(startLong, startLat);
    	org.locationtech.jts.geom.Coordinate end = new org.locationtech.jts.geom.Coordinate(endLong, endLat);
    	org.locationtech.jts.geom.Coordinate[] coords = new org.locationtech.jts.geom.Coordinate[] {start, end};
   
    	//return the coordinate
    	return geoFact.createLineString(coords); 	
    }
    
    /**
     * Handler method to turn the string coordinate into a Polygon
     * @param geoFact
     * @param datum
     * @return
     */
    private static org.locationtech.jts.geom.Polygon getPolygonFromDatum(org.locationtech.jts.geom.GeometryFactory geoFact, String datum) {
    	StringTokenizer parser = new StringTokenizer(datum, "NEWS");
    	//parse location tags into two tokens, each indicating a longitudinal or latitudinal distance
    	String ns = parser.nextToken();
    	String ew = parser.nextToken();
    	
    	double NSCoor1 = 0.0;
    	double NSCoor2 = 0.0;
    	double EWCoor1 = 0.0;
    	double EWCoor2 = 0.0;
    	

    	//split ns string and ew string into two doubles
    	StringTokenizer rangeParser = new StringTokenizer(ns, " - ");    
    	NSCoor1 = Double.parseDouble(rangeParser.nextToken());
    	NSCoor2 = Double.parseDouble(rangeParser.nextToken());
    	
    	rangeParser = new StringTokenizer(ew, " - ");   
    	EWCoor1 = Double.parseDouble(rangeParser.nextToken());
    	EWCoor2 = Double.parseDouble(rangeParser.nextToken());
    	
    	//have to account for direction
    	NSCoor1 = datum.contains("S") || datum.contains("s") ? 0 - NSCoor1 : NSCoor1;
    	NSCoor2 = datum.contains("S") || datum.contains("s") ? 0 - NSCoor2 : NSCoor2;
    	EWCoor1 = datum.contains("W") || datum.contains("w") ? 0 - EWCoor1 : EWCoor1;
    	EWCoor2 = datum.contains("W") || datum.contains("w") ? 0 - EWCoor2 : EWCoor2;
    	
    	//increment or decrement the site datum accordingly to calculate the coordinate of these points
    	double startLat =  siteDatum.y + (NSCoor1/conversion);
    	double endLat = siteDatum.y + (NSCoor2/conversion);
    	double startLong = siteDatum.x + (EWCoor1/conversion);
    	double endLong = siteDatum.x + (EWCoor2/conversion);
    	
    	//create coords from points. Have to follow this order or the polygons will be X-shaped instead of rectilinear
    	org.locationtech.jts.geom.Coordinate coord1 = new org.locationtech.jts.geom.Coordinate(startLong, startLat);
    	org.locationtech.jts.geom.Coordinate coord2 = new org.locationtech.jts.geom.Coordinate(startLong, endLat);
    	org.locationtech.jts.geom.Coordinate coord3 = new org.locationtech.jts.geom.Coordinate(endLong, endLat);
    	org.locationtech.jts.geom.Coordinate coord4 = new org.locationtech.jts.geom.Coordinate(endLong, startLat);
    	org.locationtech.jts.geom.Coordinate[] coords = new org.locationtech.jts.geom.Coordinate[] {coord1, coord2, coord3, coord4, coord1};
   
    	//return the coordinate
    	return geoFact.createPolygon(coords); 	
    }
}