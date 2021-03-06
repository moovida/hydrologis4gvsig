package org.hortonmachine.gvsig.epanet.core;
import java.awt.Desktop;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.gvsig.app.ApplicationLocator;
import org.gvsig.app.ApplicationManager;
import org.gvsig.app.project.ProjectManager;
import org.gvsig.app.project.documents.Document;
import org.gvsig.app.project.documents.view.ViewDocument;
import org.gvsig.fmap.mapcontext.layers.FLayers;
import org.gvsig.tools.ToolsLocator;
import org.gvsig.tools.dynobject.DynObject;
import org.gvsig.tools.i18n.I18nManager;
import org.gvsig.tools.swing.api.ToolsSwingLocator;
import org.gvsig.tools.swing.api.threadsafedialogs.ThreadSafeDialogsManager;
import org.gvsig.tools.swing.api.windowmanager.WindowManager;
import org.gvsig.tools.swing.api.windowmanager.WindowManager.MODE;
import org.hortonmachine.gears.libs.exceptions.ModelsUserCancelException;
import org.hortonmachine.gears.libs.monitor.IHMProgressMonitor;
import org.hortonmachine.gears.libs.monitor.LogProgressMonitor;
import org.hortonmachine.gears.utils.CrsUtilities;
import org.hortonmachine.gears.utils.features.FeatureUtilities;
import org.hortonmachine.gears.utils.files.FileUtilities;
import org.hortonmachine.gvsig.base.HMUtilities;
import org.hortonmachine.gvsig.base.ProjectUtilities;
import org.hortonmachine.gvsig.base.utils.console.LogConsoleController;
import org.hortonmachine.gvsig.epanet.CreateProjectFilesExtension;
import org.hortonmachine.gvsig.epanet.RunEpanetExtension;
import org.hortonmachine.gvsig.epanet.SyncEpanetShapefilesExtension;
import org.hortonmachine.gvsig.epanet.database.EpanetRun;
import org.hortonmachine.gvsig.epanet.database.JunctionsResultsTable;
import org.hortonmachine.gvsig.epanet.database.JunctionsTable;
import org.hortonmachine.gvsig.epanet.database.PipesResultsTable;
import org.hortonmachine.gvsig.epanet.database.PipesTable;
import org.hortonmachine.gvsig.epanet.database.PumpsResultsTable;
import org.hortonmachine.gvsig.epanet.database.PumpsTable;
import org.hortonmachine.gvsig.epanet.database.ReservoirsResultsTable;
import org.hortonmachine.gvsig.epanet.database.ReservoirsTable;
import org.hortonmachine.gvsig.epanet.database.TanksResultsTable;
import org.hortonmachine.gvsig.epanet.database.TanksTable;
import org.hortonmachine.gvsig.epanet.database.ValvesResultsTable;
import org.hortonmachine.gvsig.epanet.database.ValvesTable;
import org.hortonmachine.hmachine.modules.networktools.epanet.OmsEpanetInpGenerator;
import org.hortonmachine.hmachine.modules.networktools.epanet.OmsEpanetParametersOptions;
import org.hortonmachine.hmachine.modules.networktools.epanet.OmsEpanetParametersTime;
import org.hortonmachine.hmachine.modules.networktools.epanet.core.EpanetConstants;
import org.hortonmachine.hmachine.modules.networktools.epanet.core.EpanetException;
import org.hortonmachine.hmachine.modules.networktools.epanet.core.EpanetFeatureTypes;
import org.hortonmachine.hmachine.modules.networktools.epanet.core.EpanetFeatureTypes.Junctions;
import org.hortonmachine.hmachine.modules.networktools.epanet.core.EpanetFeatureTypes.Pipes;
import org.hortonmachine.hmachine.modules.networktools.epanet.core.EpanetFeatureTypes.Pumps;
import org.hortonmachine.hmachine.modules.networktools.epanet.core.EpanetFeatureTypes.Reservoirs;
import org.hortonmachine.hmachine.modules.networktools.epanet.core.EpanetFeatureTypes.Tanks;
import org.hortonmachine.hmachine.modules.networktools.epanet.core.EpanetFeatureTypes.Valves;
import org.hortonmachine.hmachine.modules.networktools.epanet.core.OptionParameterCodes;
import org.hortonmachine.hmachine.modules.networktools.epanet.core.TimeParameterCodes;
import org.joda.time.DateTime;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.WKTWriter;

import jwizardcomponent.FinishAction;
import jwizardcomponent.JWizardComponents;
import jwizardcomponent.JWizardPanel;
import jwizardcomponent.Utilities;
import jwizardcomponent.frame.JWizardFrame;

/**
 * Epanet run wizard.
 * 
 * @author Andrea Antonello (www.hydrologis.com)
 */
public class RunEpanetWizard extends JWizardFrame {
    private static final String PREFERENCE_EPANET_MEMORY = "EPANET_MEMORY";

    private static final Logger logger = LoggerFactory.getLogger(RunEpanetWizard.class);

    private static final long serialVersionUID = 1L;

    public static final int PANEL_GeneralParametersWizardPage = 0;
    public static final int PANEL_TimeParametersWizardPage = 1;
    public static final int PANEL_OptionsParametersWizardPage = 2;
    public static final int PANEL_ExtraFilesWizardPage = 3;

    private JTextField P1_titleText;
    private JTextField P1_descriptionText;
    private JTextField P1_userText;

    private HashMap<TimeParameterCodes, JTextField> timeKey2ValueMap = new HashMap<TimeParameterCodes, JTextField>();
    private HashMap<OptionParameterCodes, JTextField> optionsKey2ValueMap = new HashMap<OptionParameterCodes, JTextField>();

    private ThreadSafeDialogsManager dialogManager;

    private static final String EXTRAS_FOLDER_PATH = "EXTRAS_FOLDER_PATH";
    private static final String INP_FILE_PATH = "INP_FILE_PATH";
    private static final String RULES_FILE_PATH = "RULES_FILE_PATH";
    private static final String CONTROL_FILE_PATH = "CONTROL_FILE_PATH";
    private static final String DEMANDS_FILE_PATH = "DEMANDS_FILE_PATH";
    private static final String DB_FILE_PATH = "DB_FILE_PATH";
    private JTextField P5_dbText;
    private JTextField P4_extrasText;
    private JTextField P4_demandsText;
    private JTextField P4_controlText;
    private JTextField P4_rulesText;
    private JTextField P4_inpText;

    private I18nManager i18nManager;

    private ProjectManager projectManager;

    private ApplicationManager applicationManager;

    private DynObject preferences;

    private HashMap<String, String> prefsMap = new HashMap<String, String>();

    public RunEpanetWizard() {
        super();

        applicationManager = ApplicationLocator.getManager();

        dialogManager = ToolsSwingLocator.getThreadSafeDialogsManager();
        i18nManager = ToolsLocator.getI18nManager();
        projectManager = applicationManager.getProjectManager();

        preferences = ProjectUtilities.getPluginPreferences(RunEpanetExtension.class);
        Object prefsMapTmp = preferences.getDynValue(PREFERENCE_EPANET_MEMORY);
        if (prefsMapTmp != null) {
            prefsMap = (HashMap) prefsMapTmp;
        }
        init();

    }

    private void init() {
        this.setTitle("Epanet Run Wizard");
        // this.setIconImage("Epanet Run Wizard");

        JWizardPanel panel = new GeneralParametersWizardPage(getWizardComponents());
        getWizardComponents().addWizardPanel(PANEL_GeneralParametersWizardPage, panel);

        panel = new TimeParametersWizardPanel(getWizardComponents());
        getWizardComponents().addWizardPanel(PANEL_TimeParametersWizardPage, panel);

        panel = new OptionsParametersWizardPanel(getWizardComponents());
        getWizardComponents().addWizardPanel(PANEL_OptionsParametersWizardPage, panel);

        panel = new ExtraFilesWizardPage(getWizardComponents());
        getWizardComponents().addWizardPanel(PANEL_ExtraFilesWizardPage, panel);

        panel = new OutputFileWizardPage(getWizardComponents());
        getWizardComponents().addWizardPanel(4, panel);

        setSize(500, 500);
        Utilities.centerComponentOnScreen(this);
    }

    @Override
    protected FinishAction createFinishAction() {
        return new FinishAction(getWizardComponents()){
            public void performAction() {
                performFinish();
            }
        };
    }

    public void performFinish() {

        final String inpFilePath = P4_inpText.getText();
        prefsMap.put(INP_FILE_PATH, inpFilePath);
        final String extrasFolderPath = P4_extrasText.getText();
        prefsMap.put(EXTRAS_FOLDER_PATH, extrasFolderPath);
        final String demandPath = P4_demandsText.getText();
        prefsMap.put(DEMANDS_FILE_PATH, demandPath);
        final String controlPath = P4_controlText.getText();
        prefsMap.put(CONTROL_FILE_PATH, controlPath);
        final String rulesPath = P4_rulesText.getText();
        prefsMap.put(RULES_FILE_PATH, rulesPath);
        final String dbPath = P5_dbText.getText();
        prefsMap.put(DB_FILE_PATH, dbPath);

        for( Entry<TimeParameterCodes, JTextField> entry : timeKey2ValueMap.entrySet() ) {
            TimeParameterCodes key = entry.getKey();
            String prefKey = key.getKey();
            String value = entry.getValue().getText();
            prefsMap.put(prefKey, value);
        }
        for( Entry<OptionParameterCodes, JTextField> entry : optionsKey2ValueMap.entrySet() ) {
            OptionParameterCodes key = entry.getKey();
            String prefKey = key.getKey();
            String value = entry.getValue().getText();
            prefsMap.put(prefKey, value);
        }

        preferences.setDynValue(PREFERENCE_EPANET_MEMORY, prefsMap);

        final IHMProgressMonitor pm = new LogProgressMonitor();
        try {

            Document activeDocument = projectManager.getCurrentProject().getActiveDocument();
            ViewDocument view = null;
            if (activeDocument instanceof ViewDocument) {
                view = (ViewDocument) activeDocument;
                if (!view.getName().equals(i18nManager.getTranslation(CreateProjectFilesExtension.MY_VIEW_NAME))) {
                    dialogManager.messageDialog("Please select the Epanet Layer View to proceed.", "ERROR",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            final FLayers layers = view.getMapContext().getLayers();

            WindowManager windowManager = ToolsSwingLocator.getWindowManager();
            final LogConsoleController logConsole = new LogConsoleController(pm);
            windowManager.showWindow(logConsole.asJComponent(), "Console Log", MODE.WINDOW);

            new Thread(new Runnable(){
                public void run() {
                    try {
                        logConsole.beginProcess("RunEpanetExtension");
                        process(layers, inpFilePath, extrasFolderPath, demandPath, rulesPath, controlPath, dbPath, pm);
                        logConsole.finishProcess();
                        logConsole.stopLogging();
                        // SwingUtilities.invokeLater(new Runnable(){
                        // public void run() {
                        // logConsole.setVisible(false);
                        // }
                        // });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }).start();

        } catch (final Exception e) {
            String message = e.getLocalizedMessage();
            if (message == null) {
                message = "An error occurred while creating the project files.";
            } else {
                message = message.replaceFirst(EpanetException.class.getCanonicalName() + ": ", "");
            }
            dialogManager.messageDialog(message, "ERROR", JOptionPane.ERROR_MESSAGE);

            logger.error(message, e);
        } finally {
            pm.done();
            setVisible(false);
            dispose();
        }
    }

    private void process( FLayers layers, String inpFilePath, String extrasFolderPath, String demandPath, String rulesPath,
            String controlPath, String dbPath, IHMProgressMonitor pm ) throws Exception {

        pm.beginTask("Reading input vector layers...", 7);
        SimpleFeatureCollection jFC = SyncEpanetShapefilesExtension.toFc(layers, EpanetFeatureTypes.Junctions.ID.getName());
        pm.worked(1);
        SimpleFeatureCollection piFC = SyncEpanetShapefilesExtension.toFc(layers, EpanetFeatureTypes.Pipes.ID.getName());
        pm.worked(1);

        if (jFC == null || piFC == null) {
            dialogManager.messageDialog("Could not find any pipes and junctions layer in the current view. Check your data.",
                    "ERROR", JOptionPane.ERROR_MESSAGE);
            return;
        }

        SimpleFeatureCollection tFC = SyncEpanetShapefilesExtension.toFc(layers, EpanetFeatureTypes.Tanks.ID.getName());
        pm.worked(1);
        SimpleFeatureCollection puFC = SyncEpanetShapefilesExtension.toFc(layers, EpanetFeatureTypes.Pumps.ID.getName());
        pm.worked(1);
        SimpleFeatureCollection vFC = SyncEpanetShapefilesExtension.toFc(layers, EpanetFeatureTypes.Valves.ID.getName());
        pm.worked(1);
        SimpleFeatureCollection rFC = SyncEpanetShapefilesExtension.toFc(layers, EpanetFeatureTypes.Reservoirs.ID.getName());
        pm.worked(1);
        pm.done();

        if (tFC == null && rFC == null) {
            dialogManager.messageDialog("One of a tanks or reservoir layer is needed to proceed. Check your data.", "ERROR",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        HashMap<TimeParameterCodes, String> timeKey2ValueMapStr = new HashMap<TimeParameterCodes, String>();
        HashMap<OptionParameterCodes, String> optionsKey2ValueMapStr = new HashMap<OptionParameterCodes, String>();
        for( Entry<TimeParameterCodes, JTextField> entry : timeKey2ValueMap.entrySet() ) {
            timeKey2ValueMapStr.put(entry.getKey(), entry.getValue().getText());
        }
        for( Entry<OptionParameterCodes, JTextField> entry : optionsKey2ValueMap.entrySet() ) {
            optionsKey2ValueMapStr.put(entry.getKey(), entry.getValue().getText());
        }

        // time params
        OmsEpanetParametersTime time = OmsEpanetParametersTime.createFromMap(timeKey2ValueMapStr);
        // options params
        OmsEpanetParametersOptions options = OmsEpanetParametersOptions.createFromMap(optionsKey2ValueMapStr);

        OmsEpanetInpGenerator gen = new OmsEpanetInpGenerator();
        gen.pm = pm;
        gen.inJunctions = jFC;
        gen.inTanks = tFC;
        gen.inPumps = puFC;
        gen.inPipes = piFC;
        gen.inValves = vFC;
        gen.inReservoirs = rFC;
        gen.outFile = inpFilePath;
        gen.inTime = time;
        gen.inOptions = options;
        if (!extrasFolderPath.equals("")) {
            gen.inExtras = extrasFolderPath;
        }
        if (!demandPath.equals("")) {
            gen.inDemand = demandPath;
        }
        if (!controlPath.equals("")) {
            gen.inControl = controlPath;
        }
        if (!rulesPath.equals("")) {
            gen.inRules = rulesPath;
        }
        gen.process();

        File inpFile = new File(inpFilePath);
        String name = inpFile.getName();
        if (name.indexOf('.') != -1) {
            name = FileUtilities.getNameWithoutExtention(inpFile);
        }
        File outputEpanetFile = new File(inpFile.getParentFile(), name + "_epanet.inp");
        ConnectionSource connectionSource = null;
        boolean canceledByUser = false;
        try {

            pm.beginTask("Connect to output database and create tables if necessary...", IHMProgressMonitor.UNKNOWN);
            String databaseUrl = "jdbc:sqlite:" + dbPath;
            connectionSource = new JdbcConnectionSource(databaseUrl);

            TableUtils.createTableIfNotExists(connectionSource, EpanetRun.class);
            TableUtils.createTableIfNotExists(connectionSource, JunctionsTable.class);
            TableUtils.createTableIfNotExists(connectionSource, PipesTable.class);
            TableUtils.createTableIfNotExists(connectionSource, PumpsTable.class);
            TableUtils.createTableIfNotExists(connectionSource, ValvesTable.class);
            TableUtils.createTableIfNotExists(connectionSource, TanksTable.class);
            TableUtils.createTableIfNotExists(connectionSource, ReservoirsTable.class);

            TableUtils.createTableIfNotExists(connectionSource, JunctionsResultsTable.class);
            TableUtils.createTableIfNotExists(connectionSource, PipesResultsTable.class);
            TableUtils.createTableIfNotExists(connectionSource, PumpsResultsTable.class);
            TableUtils.createTableIfNotExists(connectionSource, ValvesResultsTable.class);
            TableUtils.createTableIfNotExists(connectionSource, TanksResultsTable.class);
            TableUtils.createTableIfNotExists(connectionSource, ReservoirsResultsTable.class);

            Dao<EpanetRun, Long> epanetRunDao = DaoManager.createDao(connectionSource, EpanetRun.class);
            Dao<JunctionsTable, Long> junctionsDao = DaoManager.createDao(connectionSource, JunctionsTable.class);
            Dao<PipesTable, Long> pipesDao = DaoManager.createDao(connectionSource, PipesTable.class);
            Dao<PumpsTable, Long> pumpsDao = DaoManager.createDao(connectionSource, PumpsTable.class);
            Dao<ValvesTable, Long> valvesDao = DaoManager.createDao(connectionSource, ValvesTable.class);
            Dao<TanksTable, Long> tanksDao = DaoManager.createDao(connectionSource, TanksTable.class);
            Dao<ReservoirsTable, Long> reservoirsDao = DaoManager.createDao(connectionSource, ReservoirsTable.class);
            pm.done();

            String title = P1_titleText.getText();
            String descr = P1_descriptionText.getText();
            String user = P1_userText.getText();
            DateTime dateTime = new DateTime();

            EpanetRun run = new EpanetRun();
            run.setInp(FileUtilities.readFile(outputEpanetFile));
            run.setTitle(title);
            run.setDescription(descr);
            run.setUser(user);
            run.setUtcTime(dateTime);
            epanetRunDao.create(run);

            HashMap<String, JunctionsTable> jId2Table = new HashMap<String, JunctionsTable>();
            HashMap<String, PipesTable> piId2Table = new HashMap<String, PipesTable>();
            HashMap<String, PumpsTable> puId2Table = new HashMap<String, PumpsTable>();
            HashMap<String, ValvesTable> vId2Table = new HashMap<String, ValvesTable>();
            HashMap<String, TanksTable> tId2Table = new HashMap<String, TanksTable>();
            HashMap<String, ReservoirsTable> rId2Table = new HashMap<String, ReservoirsTable>();

            pm.beginTask("Importing network data to the database...", IHMProgressMonitor.UNKNOWN);
            List<SimpleFeature> jList = FeatureUtilities.featureCollectionToList(jFC);
            for( SimpleFeature j : jList ) {
                checkCancel(pm);
                Geometry g = (Geometry) j.getDefaultGeometry();
                WKTWriter r = new WKTWriter();
                String wkt = r.write(g);
                CoordinateReferenceSystem crs = j.getType().getCoordinateReferenceSystem();
                String crsCode = CrsUtilities.getCodeFromCrs(crs);
                Object id = FeatureUtilities.getAttributeCaseChecked(j, Junctions.ID.getAttributeName());
                JunctionsTable jt = new JunctionsTable();
                String idStrs = id.toString();
                jt.setId(idStrs);
                jt.setRun(run);
                jt.setWkt(wkt);
                jt.setCrsCode(crsCode);
                junctionsDao.create(jt);

                jId2Table.put(idStrs, jt);
            }
            pm.worked(1);

            List<SimpleFeature> piList = FeatureUtilities.featureCollectionToList(piFC);
            for( SimpleFeature pi : piList ) {
                checkCancel(pm);
                Object id = FeatureUtilities.getAttributeCaseChecked(pi, Pipes.ID.getAttributeName());
                String idStr = id.toString();
                if (idStr.equals(EpanetConstants.DUMMYPIPE.toString())) {
                    continue;
                }
                Geometry g = (Geometry) pi.getDefaultGeometry();
                WKTWriter r = new WKTWriter();
                String wkt = r.write(g);
                CoordinateReferenceSystem crs = pi.getType().getCoordinateReferenceSystem();
                String crsCode = CrsUtilities.getCodeFromCrs(crs);
                PipesTable pit = new PipesTable();
                pit.setId(idStr);
                pit.setRun(run);
                pit.setWkt(wkt);
                pit.setCrsCode(crsCode);
                pipesDao.create(pit);

                piId2Table.put(idStr, pit);
            }
            pm.worked(1);

            List<SimpleFeature> puList = FeatureUtilities.featureCollectionToList(puFC);
            for( SimpleFeature pu : puList ) {
                checkCancel(pm);
                Geometry g = (Geometry) pu.getDefaultGeometry();
                Geometry buffer = g.buffer(1.0);
                Geometry pipe = null;
                for( SimpleFeature pi : piList ) {
                    Geometry piGeom = (Geometry) pi.getDefaultGeometry();
                    if (buffer.intersects(piGeom)) {
                        pipe = piGeom;
                        break;
                    }
                }

                WKTWriter r = new WKTWriter();
                String wkt = r.write(g);
                String secondaryWkt = wkt;
                if (pipe != null) {
                    secondaryWkt = r.write(pipe);
                }
                CoordinateReferenceSystem crs = pu.getType().getCoordinateReferenceSystem();
                String crsCode = CrsUtilities.getCodeFromCrs(crs);
                Object id = FeatureUtilities.getAttributeCaseChecked(pu, Pumps.ID.getAttributeName());
                PumpsTable put = new PumpsTable();
                String idStr = id.toString();
                put.setId(idStr);
                put.setRun(run);
                put.setWkt(wkt);
                put.setCrsCode(crsCode);
                put.setLinkWkt(secondaryWkt);
                pumpsDao.create(put);

                puId2Table.put(idStr, put);
            }
            pm.worked(1);

            List<SimpleFeature> vList = FeatureUtilities.featureCollectionToList(vFC);
            for( SimpleFeature v : vList ) {
                checkCancel(pm);
                Geometry g = (Geometry) v.getDefaultGeometry();
                Geometry buffer = g.buffer(1.0);
                Geometry pipe = null;
                for( SimpleFeature pi : piList ) {
                    Geometry piGeom = (Geometry) pi.getDefaultGeometry();
                    if (buffer.intersects(piGeom)) {
                        pipe = piGeom;
                        break;
                    }
                }
                WKTWriter r = new WKTWriter();
                String wkt = r.write(g);
                String secondaryWkt = wkt;
                if (pipe != null) {
                    secondaryWkt = r.write(pipe);
                }
                CoordinateReferenceSystem crs = v.getType().getCoordinateReferenceSystem();
                String crsCode = CrsUtilities.getCodeFromCrs(crs);
                Object id = FeatureUtilities.getAttributeCaseChecked(v, Valves.ID.getAttributeName());
                ValvesTable vt = new ValvesTable();
                String idStr = id.toString();
                vt.setId(idStr);
                vt.setRun(run);
                vt.setWkt(wkt);
                vt.setCrsCode(crsCode);
                vt.setLinkWkt(secondaryWkt);
                valvesDao.create(vt);

                vId2Table.put(idStr, vt);
            }
            pm.worked(1);

            List<SimpleFeature> tList = FeatureUtilities.featureCollectionToList(tFC);
            for( SimpleFeature t : tList ) {
                checkCancel(pm);
                Geometry g = (Geometry) t.getDefaultGeometry();
                WKTWriter r = new WKTWriter();
                String wkt = r.write(g);
                CoordinateReferenceSystem crs = t.getType().getCoordinateReferenceSystem();
                String crsCode = CrsUtilities.getCodeFromCrs(crs);
                Object id = FeatureUtilities.getAttributeCaseChecked(t, Tanks.ID.getAttributeName());
                TanksTable tt = new TanksTable();
                String idStr = id.toString();
                tt.setId(idStr);
                tt.setRun(run);
                tt.setWkt(wkt);
                tt.setCrsCode(crsCode);
                tanksDao.create(tt);

                tId2Table.put(idStr, tt);
            }
            pm.worked(1);

            List<SimpleFeature> rList = FeatureUtilities.featureCollectionToList(rFC);
            for( SimpleFeature res : rList ) {
                checkCancel(pm);
                Geometry g = (Geometry) res.getDefaultGeometry();
                WKTWriter r = new WKTWriter();
                String wkt = r.write(g);
                CoordinateReferenceSystem crs = res.getType().getCoordinateReferenceSystem();
                String crsCode = CrsUtilities.getCodeFromCrs(crs);
                Object id = FeatureUtilities.getAttributeCaseChecked(res, Reservoirs.ID.getAttributeName());
                ReservoirsTable rt = new ReservoirsTable();
                String idStr = id.toString();
                rt.setId(idStr);
                rt.setRun(run);
                rt.setWkt(wkt);
                rt.setCrsCode(crsCode);
                reservoirsDao.create(rt);

                rId2Table.put(idStr, rt);
            }
            pm.worked(1);
            pm.done();

            EpanetRunner runner = new EpanetRunner(outputEpanetFile.getAbsolutePath());
            runner.run(time.startClockTime, time.hydraulicTimestep, pm, run, jId2Table, piId2Table, puId2Table, vId2Table,
                    tId2Table, rId2Table, connectionSource);
            String warnings = runner.getWarnings();

            dialogManager.messageDialog(warnings, "WARNING", JOptionPane.WARNING_MESSAGE);

        } catch (Exception e) {
            if (e instanceof ModelsUserCancelException) {
                pm.errorMessage(ModelsUserCancelException.DEFAULTMESSAGE);
                canceledByUser = true;
                return;
            }
            String errMsg = e.getMessage();
            if (errMsg == null || errMsg.contains("One or more errors in input file")) {
                // ignore, the report file already contains this
            } else {
                throw e;
            }
        } finally {
            /*
             * even if an exception was thrown, try to save the job done up to
             * that point. That will help the user to check where the data 
             * screwed up and understand how to solve.
             */
            try {
                if (connectionSource != null)
                    connectionSource.close();
            } catch (Exception e) {
                throw e;
            }
            if (canceledByUser) {
                return;
            }
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                try {
                    File reportFile = new File(outputEpanetFile.getAbsolutePath() + ".rpt");
                    List<String> rptLines = FileUtilities.readFileToLinesList(reportFile);
                    for( String line : rptLines ) {
                        pm.errorMessage(line);
                    }
                    // desktop.open(reportFile);
                } catch (Exception e) {
                    // try opening the folder
                    desktop.open(outputEpanetFile.getParentFile());
                }
            }

        }
    }

    private void checkCancel( IHMProgressMonitor pm ) {
        if (pm != null && pm.isCanceled()) {
            throw new ModelsUserCancelException();
        }
    }

    private class GeneralParametersWizardPage extends JWizardPanel {
        private static final long serialVersionUID = 1L;

        public GeneralParametersWizardPage( JWizardComponents wizardComponents ) {
            super(wizardComponents, "Define general parameters");
            setPanelTitle("Define general parameters");
            // setDescription("Insert general parameters to use to describe the simulation.");
            this.setLayout(new GridBagLayout());

            int row = 0;
            int col = 0;
            int width = 1;
            int height = 1;
            int times = 3;
            JLabel titleLabel = new JLabel("Title");
            Insets insets = new Insets(5, 5, 5, 5);
            add(titleLabel, new GridBagConstraints(col, row, width, height, 0.0, 0.0, GridBagConstraints.NORTHWEST,
                    GridBagConstraints.BOTH, insets, 0, 0));

            P1_titleText = new JTextField("New Epanet Run");
            add(P1_titleText, new GridBagConstraints(col + 1, row++, width * times, height, 2.0, 0.0,
                    GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, insets, 0, 0));
            DocumentListener textListener = new DocumentListener(){
                public void changedUpdate( DocumentEvent e ) {
                    update();
                }
                public void removeUpdate( DocumentEvent e ) {
                    update();
                }
                public void insertUpdate( DocumentEvent e ) {
                    update();
                }
            };
            P1_titleText.getDocument().addDocumentListener(textListener);

            JLabel descriptionLabel = new JLabel("Description");
            add(descriptionLabel, new GridBagConstraints(col, row, width, height, 0.0, 0.0, GridBagConstraints.NORTHWEST,
                    GridBagConstraints.BOTH, insets, 0, 0));
            P1_descriptionText = new JTextField("New Epanet Run Description");
            add(P1_descriptionText, new GridBagConstraints(col + 1, row++, width * times, height, 2.0, 0.0,
                    GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, insets, 0, 0));
            P1_descriptionText.getDocument().addDocumentListener(textListener);

            JLabel userLabel = new JLabel("User");
            add(userLabel, new GridBagConstraints(col, row, width, height, 0.0, 0.0, GridBagConstraints.NORTHWEST,
                    GridBagConstraints.BOTH, insets, 0, 0));
            String userStr = System.getProperty("user.name");
            P1_userText = new JTextField(userStr);
            add(P1_userText, new GridBagConstraints(col + 1, row++, width * times, height, 2.0, 0.0, GridBagConstraints.NORTHWEST,
                    GridBagConstraints.BOTH, insets, 0, 0));
            P1_userText.getDocument().addDocumentListener(textListener);

            update();
        }

        public void update() {
            setNextButtonEnabled(allTextsFilled());
            setFinishButtonEnabled(false);
            setBackButtonEnabled(false);
        }

        private boolean allTextsFilled() {
            return P1_titleText.getText().trim().length() > 0 && P1_descriptionText.getText().trim().length() > 0
                    && P1_userText.getText().trim().length() > 0;
        }
    }

    private class TimeParametersWizardPanel extends JWizardPanel {
        private static final long serialVersionUID = 1L;

        public TimeParametersWizardPanel( JWizardComponents wizardComponents ) {
            super(wizardComponents, "Define time parameters");
            setPanelTitle("Define time parameters");
            // setDescription("Insert the time parameters to use during the simulation.");
            this.setLayout(new GridBagLayout());

            DocumentListener textListener = new DocumentListener(){
                public void changedUpdate( DocumentEvent e ) {
                    update();
                }
                public void removeUpdate( DocumentEvent e ) {
                    update();
                }
                public void insertUpdate( DocumentEvent e ) {
                    update();
                }
            };

            TimeParameterCodes[] values = TimeParameterCodes.values();
            int row = 0;
            int col = 0;
            int width = 1;
            int height = 1;
            int times = 3;
            Insets insets = new Insets(5, 5, 5, 5);
            for( TimeParameterCodes timeParameterCode : values ) {
                String label = timeParameterCode.getKey();
                String tooltip = timeParameterCode.getDescription();

                String value = prefsMap.get(label);
                if (value == null)
                    value = timeParameterCode.getDefaultValue();
                JLabel codeLabel = new JLabel(label);
                codeLabel.setToolTipText(tooltip);
                add(codeLabel, new GridBagConstraints(col, row, width, height, 0.0, 0.0, GridBagConstraints.NORTHWEST,
                        GridBagConstraints.BOTH, insets, 0, 0));

                JTextField textField = new JTextField(value);
                add(textField, new GridBagConstraints(col + 1, row++, width * times, height, 2.0, 0.0,
                        GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, insets, 0, 0));
                textField.getDocument().addDocumentListener(textListener);

                timeKey2ValueMap.put(timeParameterCode, textField);
            }

            update();
        }

        public void update() {
            setNextButtonEnabled(allTextsFilled());
            setFinishButtonEnabled(false);
            setBackButtonEnabled(true);
        }

        private boolean allTextsFilled() {
            for( JTextField textField : timeKey2ValueMap.values() ) {
                if (textField.getText().trim().length() == 0) {
                    return false;
                }
            }
            return true;
        }
    }

    private class OptionsParametersWizardPanel extends JWizardPanel {
        private static final long serialVersionUID = 1L;

        public OptionsParametersWizardPanel( JWizardComponents wizardComponents ) {
            super(wizardComponents, "Define options parameters");
            setPanelTitle("Define options parameters");
            // setDescription("Insert the options parameters to use during the simulation..");
            this.setLayout(new GridBagLayout());

            DocumentListener textListener = new DocumentListener(){
                public void changedUpdate( DocumentEvent e ) {
                    update();
                }
                public void removeUpdate( DocumentEvent e ) {
                    update();
                }
                public void insertUpdate( DocumentEvent e ) {
                    update();
                }
            };

            OptionParameterCodes[] values = OptionParameterCodes.values();
            int row = 0;
            int col = 0;
            int width = 1;
            int height = 1;
            int times = 3;
            Insets insets = new Insets(5, 5, 5, 5);
            for( OptionParameterCodes optionParameterCode : values ) {
                String label = optionParameterCode.getKey();
                String tooltip = optionParameterCode.getDescription();

                String value = prefsMap.get(label);
                if (value == null)
                    value = optionParameterCode.getDefault();
                JLabel codeLabel = new JLabel(label);
                codeLabel.setToolTipText(tooltip);
                add(codeLabel, new GridBagConstraints(col, row, width, height, 0.0, 0.0, GridBagConstraints.NORTHWEST,
                        GridBagConstraints.BOTH, insets, 0, 0));

                JTextField textField = new JTextField(value);
                add(textField, new GridBagConstraints(col + 1, row++, width * times, height, 2.0, 0.0,
                        GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, insets, 0, 0));
                textField.getDocument().addDocumentListener(textListener);

                optionsKey2ValueMap.put(optionParameterCode, textField);
            }

            update();
        }

        public void update() {
            setNextButtonEnabled(allTextsFilled());
            setFinishButtonEnabled(false);
            setBackButtonEnabled(true);
        }

        private boolean allTextsFilled() {
            for( JTextField textField : optionsKey2ValueMap.values() ) {
                if (textField.getText().trim().length() == 0) {
                    return false;
                }
            }
            return true;
        }
    }

    private class ExtraFilesWizardPage extends JWizardPanel {
        private static final long serialVersionUID = 1L;

        public ExtraFilesWizardPage( JWizardComponents wizardComponents ) {
            super(wizardComponents, "Additional files and folders");
            setPanelTitle("Additional files and folders");
            // setDescription("Define additional files and folders to consider.");
            this.setLayout(new GridBagLayout());
            DocumentListener textListener = new DocumentListener(){
                public void changedUpdate( DocumentEvent e ) {
                    update();
                }
                public void removeUpdate( DocumentEvent e ) {
                    update();
                }
                public void insertUpdate( DocumentEvent e ) {
                    update();
                }
            };

            int col = 0;
            int width = 1;
            int height = 1;
            int times = 3;
            Insets insets = new Insets(5, 5, 5, 5);

            GridBagConstraints c = new GridBagConstraints();
            c.gridx = col;
            c.gridy = 0;
            c.gridwidth = width;
            c.gridheight = height;
            c.weightx = 0.0;
            c.weighty = 0.0;
            c.anchor = GridBagConstraints.PAGE_START;
            c.fill = GridBagConstraints.BOTH;
            c.insets = insets;
            c.ipadx = 0;
            c.ipady = 0;

            /*
             * extras folder
             */
            String label = "Extra files folder";
            String tooltip = "Folder containing pattern files, curves or demands to consider.";
            JLabel extrasLabel = new JLabel(label);
            extrasLabel.setToolTipText(tooltip);
            add(extrasLabel, c);

            String value = prefsMap.get(EXTRAS_FOLDER_PATH);
            if (value == null) {
                value = "";
            }
            P4_extrasText = new JTextField(value);
            c.gridx = col + 1;
            c.weightx = 1.0;
            add(P4_extrasText, c);
            P4_extrasText.getDocument().addDocumentListener(textListener);

            JButton extrasButton = new JButton("...");
            c.gridx = col + 2;
            c.weightx = 0.0;
            add(extrasButton, c);
            extrasButton.addActionListener(new ActionListener(){
                public void actionPerformed( ActionEvent e ) {
                    File[] folders = dialogManager.showOpenDirectoryDialog("Select folder", HMUtilities.getLastFile());
                    if (folders != null && folders.length > 0) {
                        String absolutePath = folders[0].getAbsolutePath();
                        HMUtilities.setLastPath(absolutePath);
                        P4_extrasText.setText(absolutePath);
                        update();
                    }
                }
            });

            c.gridy = c.gridy + 1;

            /*
             * demands file
             */
            label = "Demand file";
            tooltip = "Extra demand file to consider.";
            JLabel demandsLabel = new JLabel(label);
            demandsLabel.setToolTipText(tooltip);
            c.gridx = col;
            add(demandsLabel, c);

            value = prefsMap.get(DEMANDS_FILE_PATH);
            if (value == null) {
                value = "";
            }
            P4_demandsText = new JTextField(value);
            c.gridx = col + 1;
            c.weightx = 1.0;
            add(P4_demandsText, c);
            P4_demandsText.getDocument().addDocumentListener(textListener);

            JButton demandsButton = new JButton("...");
            c.gridx = col + 2;
            c.weightx = 0.0;
            add(demandsButton, c);
            demandsButton.addActionListener(new ActionListener(){
                public void actionPerformed( ActionEvent e ) {
                    File[] files = dialogManager.showOpenFileDialog("Select demands file", HMUtilities.getLastFile());
                    if (files != null && files.length > 0) {
                        String absolutePath = files[0].getAbsolutePath();
                        HMUtilities.setLastPath(absolutePath);
                        P4_demandsText.setText(absolutePath);
                        update();
                    }
                }
            });

            c.gridy = c.gridy + 1;

            /*
             * control file
             */
            label = "Control file";
            tooltip = "Extra control file to consider.";
            JLabel controlLabel = new JLabel(label);
            controlLabel.setToolTipText(tooltip);
            c.gridx = col;
            add(controlLabel, c);

            value = prefsMap.get(CONTROL_FILE_PATH);
            if (value == null) {
                value = "";
            }
            P4_controlText = new JTextField(value);
            c.gridx = col + 1;
            c.weightx = 1.0;
            add(P4_controlText, c);
            P4_controlText.getDocument().addDocumentListener(textListener);

            JButton controlButton = new JButton("...");
            c.gridx = col + 2;
            c.weightx = 0.0;
            add(controlButton, c);
            controlButton.addActionListener(new ActionListener(){
                public void actionPerformed( ActionEvent e ) {
                    File[] files = dialogManager.showOpenFileDialog("Select control file", HMUtilities.getLastFile());
                    if (files != null && files.length > 0) {
                        String absolutePath = files[0].getAbsolutePath();
                        HMUtilities.setLastPath(absolutePath);
                        P4_controlText.setText(absolutePath);
                        update();
                    }
                }
            });

            c.gridy = c.gridy + 1;

            /*
             * rules file
             */
            label = "Rules file";
            tooltip = "Extra rules file to consider.";
            JLabel rulesLabel = new JLabel(label);
            rulesLabel.setToolTipText(tooltip);
            c.gridx = col;
            add(rulesLabel, c);

            value = prefsMap.get(RULES_FILE_PATH);
            if (value == null) {
                value = "";
            }
            P4_rulesText = new JTextField(value);
            c.gridx = col + 1;
            c.weightx = 1.0;
            add(P4_rulesText, c);
            P4_rulesText.getDocument().addDocumentListener(textListener);

            JButton rulesButton = new JButton("...");
            c.gridx = col + 2;
            c.weightx = 0.0;
            add(rulesButton, c);
            rulesButton.addActionListener(new ActionListener(){
                public void actionPerformed( ActionEvent e ) {
                    File[] files = dialogManager.showOpenFileDialog("Select rules file", HMUtilities.getLastFile());
                    if (files != null && files.length > 0) {
                        String absolutePath = files[0].getAbsolutePath();
                        HMUtilities.setLastPath(absolutePath);
                        P4_rulesText.setText(absolutePath);
                        update();
                    }
                }
            });

            c.gridy = c.gridy + 1;

            /*
             * inp file
             */
            label = "Inp file";
            tooltip = "Path to save the INP file to.";
            JLabel inpLabel = new JLabel(label);
            inpLabel.setToolTipText(tooltip);
            c.gridx = col;
            add(inpLabel, c);

            value = prefsMap.get(INP_FILE_PATH);
            if (value == null) {
                value = "";
            }
            P4_inpText = new JTextField(value);
            c.gridx = col + 1;
            c.weightx = 1.0;
            add(P4_inpText, c);
            P4_inpText.getDocument().addDocumentListener(textListener);

            JButton inpButton = new JButton("...");
            c.gridx = col + 2;
            c.weightx = 0.0;
            add(inpButton, c);
            inpButton.addActionListener(new ActionListener(){
                public void actionPerformed( ActionEvent e ) {
                    File[] files = dialogManager.showSaveFileDialog("Insert INP file to save to", HMUtilities.getLastFile());
                    if (files != null && files.length > 0) {
                        String absolutePath = files[0].getAbsolutePath();
                        HMUtilities.setLastPath(absolutePath);
                        P4_inpText.setText(absolutePath);
                        update();
                    }
                }
            });

            update();
        }

        public void update() {
            setBackButtonEnabled(true);
            setFinishButtonEnabled(false);

            String extraFolder = P4_extrasText.getText();
            File extraFolderFile = new File(extraFolder);
            if (!extraFolder.equals("") && !extraFolderFile.exists()) {
                setNextButtonEnabled(false);
                return;
            }

            String demandsFilePath = P4_demandsText.getText();
            File demandsFile = new File(demandsFilePath);
            if (!demandsFilePath.equals("") && !demandsFile.exists()) {
                setNextButtonEnabled(false);
                return;
            }

            String controlsFilePath = P4_controlText.getText();
            File controlFile = new File(controlsFilePath);
            if (!controlsFilePath.equals("") && !controlFile.exists()) {
                setNextButtonEnabled(false);
                return;
            }

            String inpFilePath = P4_inpText.getText();
            File inpFile = new File(inpFilePath);
            if (inpFilePath.equals("") || inpFile.getParentFile() == null || !inpFile.getParentFile().exists()) {
                setNextButtonEnabled(false);
                return;
            }

            setNextButtonEnabled(true);
        }

    }

    private class OutputFileWizardPage extends JWizardPanel {
        private static final long serialVersionUID = 1L;

        public OutputFileWizardPage( JWizardComponents wizardComponents ) {
            super(wizardComponents, "Output database");
            setPanelTitle("Output database");

            this.setLayout(new GridBagLayout());
            DocumentListener textListener = new DocumentListener(){
                public void changedUpdate( DocumentEvent e ) {
                    update();
                }
                public void removeUpdate( DocumentEvent e ) {
                    update();
                }
                public void insertUpdate( DocumentEvent e ) {
                    update();
                }
            };

            int row = 0;
            int col = 0;
            int width = 1;
            int height = 1;
            int times = 3;
            Insets insets = new Insets(5, 5, 5, 5);

            GridBagConstraints c = new GridBagConstraints();
            c.gridx = col;
            c.gridy = 0;
            c.gridwidth = width;
            c.gridheight = height;
            c.weightx = 0.0;
            c.weighty = 0.0;
            c.anchor = GridBagConstraints.PAGE_START;
            c.fill = GridBagConstraints.BOTH;
            c.insets = insets;
            c.ipadx = 0;
            c.ipady = 0;

            String label = "Output sqlite db";
            String tooltip = "The output database into which to store the simulation.";
            JLabel dbLabel = new JLabel(label);
            dbLabel.setToolTipText(tooltip);
            add(dbLabel, c);

            String value = prefsMap.get(DB_FILE_PATH);
            if (value == null) {
                value = "";
            }
            P5_dbText = new JTextField(value);
            c.gridx = col + 1;
            c.weightx = 1.0;
            add(P5_dbText, c);
            P5_dbText.getDocument().addDocumentListener(textListener);

            JButton dbButton = new JButton("...");
            c.gridx = col + 2;
            c.weightx = 0.0;
            add(dbButton, c);
            dbButton.addActionListener(new ActionListener(){
                public void actionPerformed( ActionEvent e ) {
                    File[] files = dialogManager.showSaveFileDialog("Insert sqlite database to save to",
                            HMUtilities.getLastFile());
                    if (files != null && files.length > 0) {
                        String absolutePath = files[0].getAbsolutePath();
                        HMUtilities.setLastPath(absolutePath);
                        P5_dbText.setText(absolutePath);
                        update();
                    }
                }
            });

            update();
        }

        public void update() {
            setNextButtonEnabled(false);
            setBackButtonEnabled(true);

            String dbPath = P5_dbText.getText();
            File dbFile = new File(dbPath);
            if (!(dbPath.equals("") || dbFile.getParentFile().exists())) {
                setFinishButtonEnabled(false);
                return;
            }

            setFinishButtonEnabled(true);
        }

    }

}