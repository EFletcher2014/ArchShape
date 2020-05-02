package org.geotools.ArchShape;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

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
        final SimpleFeatureType TYPE = DataUtilities.createType("Location",
                "the_geom:Point:srid=4326," + // <- the geometry attribute: Point type
        		"point type:String," +
                "excCoord:String"
        );
        
        /*
         * We create a FeatureCollection into which we will put each Feature created from a record
         * in the input csv data file
         */
        DefaultFeatureCollection collection = new DefaultFeatureCollection();
        
        /*
         * GeometryFactory will be used to create the geometry attribute of each feature (a Point
         * object for the location)
         */
        org.locationtech.jts.geom.GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory(null);

        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(TYPE);

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
                    siteDatum = new org.locationtech.jts.geom.Coordinate(longitude, latitude);
                    org.locationtech.jts.geom.Point point = geometryFactory.createPoint(siteDatum);

                    featureBuilder.add(point);
                    featureBuilder.add(label);
                    featureBuilder.add("N0E0"); //Assumes datum point is N0E0--TODO: allow user to input this
                    SimpleFeature feature = featureBuilder.buildFeature(null);
                    collection.add(feature);
                }
            }
            
        } finally {
            reader.close();
        }
        
        
        //Now, open another GUI window to allow the user to input file containing relational location tags
        file = JFileDataStoreChooser.showOpenFile("csv", null);
        if (file == null) {
            return;
        }
        
        reader = new BufferedReader(new FileReader(file));
        try {
            /* First line of the data file is the header */
            String line = reader.readLine();
            System.out.println("Header: " + line);

            for (line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.trim().length() > 0) { // skip blank lines
                    String tokens[] = line.split("\\,");

                    //Assumes that table contains two columns: a relational location tag and a description of the object found there
                    //TODO: Make this more flexible
                    String excCoord = tokens[1];
                    String excObj = tokens[2];
                    

                    //Add coordinate, location tag, and description to the shapefile
                    featureBuilder.add(geometryFactory.createPoint(getCoordinateFromDatum(excCoord)));
                    featureBuilder.add(excObj);
                    featureBuilder.add(excCoord);
                    SimpleFeature feature = featureBuilder.buildFeature(null);
                    collection.add(feature); //datum and relational points will all be written to one shapefile
                }
            }
            
        } finally {
            reader.close();
        }
        
        /*
         * Create the new shapefile
         */
        File newFile = getNewShapeFile(file);

        ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();

        Map<String, Serializable> params = new HashMap<String, Serializable>();
        params.put("url", newFile.toURI().toURL());
        params.put("create spatial index", Boolean.TRUE);

        ShapefileDataStore newDataStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(params);
        newDataStore.createSchema(TYPE);

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
            System.exit(0); // success!
        } else {
            System.out.println(typeName + " does not support read/write access");
            System.exit(1);
        }
    }
    
    /**
     * Prompt the user for the name and path to use for the output shapefile
     * 
     * @param csvFile
     *            the input csv file used to create a default shapefile name
     * 
     * @return name and path for the shapefile as a new File object
     */
    private static File getNewShapeFile(File csvFile) {
        String path = csvFile.getAbsolutePath();
        String newPath = path.substring(0, path.length() - 4) + ".shp";

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
}