# ArchShape
This program automatically creates shapefiles from archaeological excavation documentation for mapping in a GIS.

# Introduction
Archaeological excavation is an inherently destructive process. Once a site is excavated, it can never be fully reconstructed. To preserve sites for future research, archaeologists document their excavations in detailed notes, maps, and reports. Decades of archaeological excavations have created an overwhelming corpus of this "grey literature" in varying formats, predominantly paper records. As these records are rarely fully digitized or shared, they are not effective at facilitating further research.

As software such as GIS becomes more prevalent in archaeology, archaeologists must find ways to integrate it with these important grey literature datasets, or risk making decades of work obsolete. Archaeologists should emphasize efforts to create digitizing tools such as Optical Character Recognition models for archaeological records, Computational Text Analysis tools to analyze grey literature, and tools to digitize maps (a major component of archaeological documentation). This project aims to tackle that final issue: digitizing archaeological maps.

# Using ArchShape as a Standalone Tool
ArchShape can be used as a standalone tool to create shapefiles from coordinates. ArchShape takes a `.csv` file containing longitudes and latitudes and "translates" them into labeled points on a shapefile for import into a GIS. To use it, run the program in an IDE and input a `.csv` file when prompted. 

# Using ArchShape to Digitize Archaeological Maps
ArchShape is intended to be used with [ArchLocateR](https://github.com/EFletcher2014/ArchLocateR) as part of a pipeline to digitize archaeological maps. This pipeline can take `.pdf` scans of archaeological field notes, make them machine-readable, identify location information in them, and turn this information into a map for use in GIS. I will outline this process here.
<img align="right" width="290" height="700" src="https://github.com/EFletcher2014/ArchShape/blob/master/Digitizing%20Archaeological%20Maps.png?raw=true">

## Step 1: Digitize Written Records
This mapping tool works by identifying location information in written texts. Therefore, it must start with machine-readable archaeological texts (specifically field notes). If your records are already machine-readable (for instance, if they were transcribed), you can skip this step. If transcription is not feasible for your dataset, you can use an OCR model such as Tesseract to have a computer perform this transcription. If your records are typed or typewritten, basic Tesseract may be very successful. However, it is likely that you may have to train Tesseract to be more successful.

## Step 2: Use [ArchLocateR](https://github.com/EFletcher2014/ArchLocateR) to Identify Location Data in Written Records
Once your field notes are in a format with which the computer can interact (specifically a `.txt` file), use [ArchLocateR](https://github.com/EFletcher2014/ArchLocateR) to parse them for location information. [ArchLocateR](https://github.com/EFletcher2014/ArchLocateR) uses a regular expression to identify excavation coordinates taking the form N1E1 (for example). Once it identifies a coordinate, ArchLocateR will pull the nearest chain of nouns and adjectives from the text as a description of the object found at that location. [ArchLocateR](https://github.com/EFletcher2014/ArchLocateR) then uses the site's datum coordinate to convert these relative coordinates into true coordinates. It stores these true coordinates, relative coordinates, and description in a `.csv` file.

## Step 3: Use ArchShape to Map in GIS
Finally, use ArchShape to convert this `.csv` into a series of points in a shapefile. Simply run ArchShape and input the file when prompted. ArchShape will convert it into a shapefile which can be opened with the GIS software of your choice.
