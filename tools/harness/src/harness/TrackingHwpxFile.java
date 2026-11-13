/* Copyright (C) 2026 ebandal
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package harness;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.zip.DataFormatException;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import HwpDoc.HwpxFile;
import HwpDoc.Exception.HwpParseException;

/** getDocument 결과를 DomTracker 프록시로 감싸는 HwpxFile */
public class TrackingHwpxFile extends HwpxFile {
    private final DomTracker tracker;

    public TrackingHwpxFile(File file, DomTracker tracker) throws FileNotFoundException {
        super(file);
        this.tracker = tracker;
    }

    @Override
    public Document getDocument(String entryName) throws IOException, ParserConfigurationException, SAXException,
                                                         DataFormatException, HwpParseException {
        return tracker.wrap(entryName, super.getDocument(entryName));
    }
}
