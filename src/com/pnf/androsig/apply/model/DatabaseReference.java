/*
 * JEB Copyright (c) PNF Software, Inc.
 * All rights reserved.
 * This file shall not be distributed or reused, in part or in whole.
 */
package com.pnf.androsig.apply.model;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.pnf.androsig.apply.matcher.DatabaseReferenceFile;
import com.pnf.androsig.apply.model.MethodSignature.MethodSignatureRevision;
import com.pnfsoftware.jeb.util.base.Couple;
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

    /** file list containing a hashcode, with hashcode as key */
    private Map<String, Set<String>> allTightHashcodes = new HashMap<>();
    private Map<String, Set<String>> allLooseHashcodes = new HashMap<>();
    private Map<String, Set<String>> allClasses = new HashMap<>();

    private SignatureFileFactory signatureFileFactory = new SignatureFileFactory();

    private int allSignatureFileCount = 0;

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
        Runtime rt = Runtime.getRuntime();
        long memused = rt.totalMemory() - rt.freeMemory();
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
            long newmemused = rt.totalMemory() - rt.freeMemory();
            if(newmemused - memused > 1_000_000_000L) {
                // Attempt gc before jeb asks for memory
                System.gc();
                memused = rt.totalMemory() - rt.freeMemory();
            }
        }
    }

    private boolean loadHashCodes(File sigFile) {
        return SignatureFileFactory.populate(sigFile, allTightHashcodes, allLooseHashcodes, allClasses);
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
        Set<String> res = allTightHashcodes.get(hashcode);
        return res == null ? null: new ArrayList<>(res);
    }

    public List<String> getFilesContainingLooseHashcode(String hashcode) {
        Set<String> res = allLooseHashcodes.get(hashcode);
        return res == null ? null: new ArrayList<>(res);
    }

    public List<String> getFilesContainingClass(String className) {
        Set<String> res = allClasses.get(className);
        return res == null ? null: new ArrayList<>(res);
    }

    @SuppressWarnings("resource")
    public List<MethodSignature> getSignatureLines(String file, String hashcode, boolean tight) {
        ISignatureFile sigFile = signatureFileFactory.getSignatureFile(file);
        return tight ? sigFile.getTightSignatures(hashcode): sigFile.getLooseSignatures(hashcode);
    }

    public List<MethodSignature> getSignatureLines(DatabaseReferenceFile file, String hashcode, boolean tight) {
        List<MethodSignature> sigs = getSignatureLines(file.file, hashcode, tight);
        Set<String> versions = file.getAvailableVersions();
        return filterVersions(sigs, versions);
    }

    @SuppressWarnings("resource")
    public List<MethodSignature> getSignaturesForClassname(String file, String className, boolean exactName) {
        ISignatureFile sigFile = signatureFileFactory.getSignatureFile(file);
        return sigFile.getSignaturesForClassname(className, exactName);
    }

    public List<MethodSignature> getSignaturesForClassname(DatabaseReferenceFile file, String className,
            boolean exactName) {
        List<MethodSignature> sigs = getSignaturesForClassname(file.file, className, exactName);
        Set<String> versions = file.getAvailableVersions();
        return filterVersions(sigs, versions);
    }

    private List<MethodSignature> filterVersions(List<MethodSignature> sigs, Set<String> versions) {
        if(sigs != null && versions != null && !versions.isEmpty()) {
            List<MethodSignature> versioned = new ArrayList<>();
            for(MethodSignature sig: sigs) {
                if(intersect(versions, sig.getVersions())) {
                    versioned.add(sig);
                }
            }
            return versioned;
            //return sigs.stream().filter(m -> intersect(versions, m.getVersions())).collect(Collectors.toList());
        }
        return sigs;
    }

    private boolean intersect(Set<String> versions, String[] versions2) {
        if(versions2 == null || versions2.length == 0 || versions == null || versions.size() == 0) {
            return true;
        }
        for(String v: versions2) {
            if(versions.contains(v)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("resource")
    public LibraryInfo getLibraryInfos(String file, String className) {
        ISignatureFile sigFile = signatureFileFactory.getSignatureFile(file);
        if(className != null && !sigFile.hasSignaturesForClassname(className)) {
            return null;
        }
        return sigFile.getLibraryInfos();
    }

    public Map<String, ISignatureFile> getLoadedSignatureFiles() {
        return signatureFileFactory.getLoadedSignatureFiles();
    }

    public void close() {
        signatureFileFactory.close();
    }

    @SuppressWarnings("resource")
    public Couple<String, List<String>> getParentForClassname(String file, String className) {
        ISignatureFile sigFile = signatureFileFactory.getSignatureFile(file);
        List<MethodSignature> sigs = sigFile.getParent(className);
        if(sigs == null || sigs.size() != 1) {
            return null;
        }
        return new Couple<>(sigs.get(0).getTargetSuperType(), sigs.get(0).getTargetInterfaces());
    }

    @SuppressWarnings("resource")
    public Couple<String, List<String>> getParentForClassname(DatabaseReferenceFile refFile, String className) {
        ISignatureFile sigFile = signatureFileFactory.getSignatureFile(refFile.file);
        List<MethodSignature> rawSigs = sigFile.getParent(className);
        if(rawSigs == null || rawSigs.isEmpty()) {
            return null;
        }
        Set<String> versions = refFile.getAvailableVersions();
        List<MethodSignature> sigs = filterVersions(rawSigs, versions);
        if(sigs.size() != 1) {
            logger.warn("Parent of %s can not be found for current version", className);
            if(rawSigs.size() == 1) {
                sigs = rawSigs;
            }
            else {
                return null;
            }
        }
        MethodSignature sig = sigs.get(0);
        String parent = null;
        Set<String> interfaces = null;
        if (sig.getRevisions().size() == 1) {
            return new Couple<>(sig.getTargetSuperType(), sig.getTargetInterfaces());
        }
        boolean firstFound = false;
        for(MethodSignatureRevision rev: sig.getRevisions()) {
            if(versions != null) {
                boolean found = false;
                for(String v: rev.getVersions()) {
                    if(versions.contains(v)) {
                        found = true;
                    }
                }
                if(!found) {
                    continue;
                }
            }
            if (!firstFound) {
                firstFound = true;
                parent = rev.getTargetSuperType();
                List<String> interfacesList = rev.getTargetInterfaces();
                interfaces = interfacesList == null ? new HashSet<>(): new HashSet<>(interfacesList);
            } else {
                // expect same signature
                if(parent != null) {
                    if(!parent.equals(rev.getTargetSuperType())) {
                        parent = null;
                    }
                }
                if(interfaces != null) {
                    List<String> altInterfaces = rev.getTargetInterfaces();
                    if(altInterfaces != null) {
                        interfaces.addAll(altInterfaces);
                    }
                }
            }
        }
        if(parent == null && interfaces == null) {
            return null;
        }
        return new Couple<>(parent, (interfaces == null || interfaces.isEmpty()) ? null: new ArrayList<>(interfaces));
    }

    public List<String> getClassList(String f) {
        List<String> classes = new ArrayList<>();
        for(Entry<String, Set<String>> class_: allClasses.entrySet()) {
            if(class_.getValue().contains(f)) {
                classes.add(class_.getKey());
            }
        }
        return classes;
    }

}
