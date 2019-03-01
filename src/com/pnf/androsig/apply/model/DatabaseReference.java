/*
 * JEB Copyright (c) PNF Software, Inc.
 * All rights reserved.
 * This file shall not be distributed or reused, in part or in whole.
 */
package com.pnf.androsig.apply.model;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.pnfsoftware.jeb.util.io.IO;
import com.pnfsoftware.jeb.util.logging.GlobalLog;
import com.pnfsoftware.jeb.util.logging.ILogger;

/**
 * Reference Hashcode that are contained in android_sigs directory. It enables user to get the list
 * of files that defines a particular hashcode (tight or loose).
 * 
 * @author Ruoxiao Wang, Cedric Lucas
 *
 */
public class DatabaseReference {
    private final ILogger logger = GlobalLog.getLogger(DatabaseReference.class);

    Map<String, List<String>> allTightHashcodes;
    Map<String, List<String>> allLooseHashcodes;

    private int allSignatureFileCount = 0;

    public DatabaseReference() {
        allTightHashcodes = new HashMap<>();
        allLooseHashcodes = new HashMap<>();
    }

    /**
     * Load all hashcodes from signature files.
     * 
     * @param sigFolder the signature folder
     */
    public void loadAllHashCodes(File sigFolder) {
        logger.info("Hashcodes loading start...");
        final long startTime = System.currentTimeMillis();
        loadAllHashCodesTemp(sigFolder);
        final long endTime = System.currentTimeMillis();
        logger.info("Hashcodes loading completed! (Execution Time: " + (endTime - startTime) / 1000 + "s)");
        logger.info("allTightHashcodes: " + allTightHashcodes.size());
        logger.info("allLooseHashcodes: " + allLooseHashcodes.size());
    }

    private void loadAllHashCodesTemp(File sigFolder) {
        for(File f: sigFolder.listFiles()) {
            if(f.isFile() && f.getName().endsWith(".sig")) {
                allSignatureFileCount++;
                if(!loadHashCodes(f)) {
                    logger.error("Cannot load signatures files: %s", f);
                }
            }
            else if(f.isDirectory()) {
                loadAllHashCodesTemp(f);
            }
        }
    }

    private boolean loadHashCodes(File sigFile) {
        List<String> lines = IO.readLinesSafe(sigFile, Charset.forName("UTF-8"));
        if(lines == null) {
            return false;
        }

        for(String line: lines) {
            line = line.trim();
            if(!MethodSignature.isSignatureLine(line)) {
                continue;
            }

            String[] subLines = MethodSignature.parseNative(line);
            if(subLines == null) {
                logger.warn("Invalid parameter signature line: " + line + " in file " + sigFile);
                continue;
            }

            String mhash_tight = MethodSignature.getTightSignature(subLines);
            if(mhash_tight != null) {
                List<String> files = allTightHashcodes.get(mhash_tight);
                if(files == null) {
                    files = new ArrayList<>();
                    allTightHashcodes.put(mhash_tight, files);
                }
                files.add(sigFile.getAbsolutePath());
            }
            String mhash_loose = MethodSignature.getLooseSignature(subLines);
            if(mhash_loose != null) {
                List<String> files = allLooseHashcodes.get(mhash_loose);
                if(files == null) {
                    files = new ArrayList<>();
                    allLooseHashcodes.put(mhash_loose, files);
                }
                files.add(sigFile.getAbsolutePath());
            }
        }
        return true;
    }

    /**
     * Get the number of signature files.
     * 
     * @return the number of signature files
     */
    public int getAllSignatureFileCount() {
        return allSignatureFileCount;
    }

    public List<String> getFilesContainingTightHashcode(String hashcode) {
        return allTightHashcodes.get(hashcode);
    }

    public List<String> getFilesContainingLooseHashcode(String hashcode) {
        return allLooseHashcodes.get(hashcode);
    }
}