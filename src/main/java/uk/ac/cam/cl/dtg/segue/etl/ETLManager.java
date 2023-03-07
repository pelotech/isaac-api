package uk.ac.cam.cl.dtg.segue.etl;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.cam.cl.dtg.segue.dao.schools.UnableToIndexSchoolsException;
import uk.ac.cam.cl.dtg.segue.database.GitDb;
import uk.ac.cam.cl.dtg.util.PropertiesManager;

/**
 * Created by Ian on 01/11/2016.
 */
class ETLManager {
    private static final Logger log = LoggerFactory.getLogger(ETLFacade.class);
    private static final String LATEST_INDEX_ALIAS = "latest";

    private final ContentIndexer indexer;

    private final PropertiesManager contentIndicesStore;


    @Inject
    ETLManager(final ContentIndexer indexer, final SchoolIndexer schoolIndexer, final GitDb database, final PropertiesManager contentIndicesStore) {
        this.indexer = indexer;
        this.contentIndicesStore = contentIndicesStore;

        private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.schedule(new ContentIndexerTask(), 300, SECONDS);

        log.info("ETL startup complete.");
    }

    void setNamedVersion(final String alias, final String version) throws Exception {
        log.info("Requested aliased version: " + alias + " - " + version);
        indexer.loadAndIndexContent(version);
        indexer.setNamedVersion(alias, version);
        log.info("Version " + version + " with alias '" + alias + "' is successfully indexed.");
    }

    // Idempotently indexes all content. If the content is already indexed no action is taken.
    private void indexContent() {
        // Load the current version aliases from config file, as well as latest, and set them.
        Map<String, String> aliasVersions = new HashMap<>();
        String latestSha = database.fetchLatestFromRemote();
        aliasVersions.put(LATEST_INDEX_ALIAS, latestSha);
        for (String alias: contentIndicesStore.stringPropertyNames()) {
          aliasVersions.put(alias, contentIndicesStore.getProperty(alias));
        }

        for (var entry : aliasVersions.entrySet()) {
            try {
                this.setNamedVersion(entry.getKey(), entry.getValue());
            } catch (VersionLockedException e) {
                log.warn("Could not index new version, lock is already held by another thread.");
            } catch (Exception e) {
                log.error("Indexing version " + newVersion + " failed.");
                e.printStackTrace();
            }
        }

        // Load the school list.
        try {
            schoolIndexer.indexSchoolsWithSearchProvider();
        } catch (UnableToIndexSchoolsException e) {
            log.error("Unable to index schools", e);
        }
    }

    private class ContentIndexerTask implements Runnable {

        @Override
        public void run() {
            log.info("Starting content indexer thread.");
            this.indexContent();
            log.info("Content indexer thread complete, waiting for next scheduled run.");
        }
    }
}
