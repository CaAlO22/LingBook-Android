package com.lingji.app.data.db;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.lingji.app.data.db.dao.FragmentDao;
import com.lingji.app.data.db.dao.FragmentDao_Impl;
import com.lingji.app.data.db.dao.NotebookPageDao;
import com.lingji.app.data.db.dao.NotebookPageDao_Impl;
import com.lingji.app.data.db.dao.SettingsDao;
import com.lingji.app.data.db.dao.SettingsDao_Impl;
import com.lingji.app.data.db.dao.SubjectDao;
import com.lingji.app.data.db.dao.SubjectDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class LingjiDatabase_Impl extends LingjiDatabase {
  private volatile SubjectDao _subjectDao;

  private volatile FragmentDao _fragmentDao;

  private volatile NotebookPageDao _notebookPageDao;

  private volatile SettingsDao _settingsDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(3) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `subjects` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `type` TEXT NOT NULL, `aggregatedNote` TEXT NOT NULL, `prevAggregatedNote` TEXT, `studyPlan` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `orderIndex` INTEGER NOT NULL, `pageIndexJson` TEXT NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `fragments` (`id` TEXT NOT NULL, `subjectId` TEXT NOT NULL, `content` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `isUnmerged` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `notebook_pages` (`id` TEXT NOT NULL, `subjectId` TEXT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `indexedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `settings` (`id` TEXT NOT NULL, `provider` TEXT NOT NULL, `baseUrl` TEXT NOT NULL, `apiKey` TEXT NOT NULL, `modelName` TEXT NOT NULL, `enableThinking` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '8b5254eb161ef4031aee097782f7be37')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `subjects`");
        db.execSQL("DROP TABLE IF EXISTS `fragments`");
        db.execSQL("DROP TABLE IF EXISTS `notebook_pages`");
        db.execSQL("DROP TABLE IF EXISTS `settings`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsSubjects = new HashMap<String, TableInfo.Column>(9);
        _columnsSubjects.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSubjects.put("title", new TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSubjects.put("type", new TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSubjects.put("aggregatedNote", new TableInfo.Column("aggregatedNote", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSubjects.put("prevAggregatedNote", new TableInfo.Column("prevAggregatedNote", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSubjects.put("studyPlan", new TableInfo.Column("studyPlan", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSubjects.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSubjects.put("orderIndex", new TableInfo.Column("orderIndex", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSubjects.put("pageIndexJson", new TableInfo.Column("pageIndexJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSubjects = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSubjects = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoSubjects = new TableInfo("subjects", _columnsSubjects, _foreignKeysSubjects, _indicesSubjects);
        final TableInfo _existingSubjects = TableInfo.read(db, "subjects");
        if (!_infoSubjects.equals(_existingSubjects)) {
          return new RoomOpenHelper.ValidationResult(false, "subjects(com.lingji.app.data.db.entities.SubjectEntity).\n"
                  + " Expected:\n" + _infoSubjects + "\n"
                  + " Found:\n" + _existingSubjects);
        }
        final HashMap<String, TableInfo.Column> _columnsFragments = new HashMap<String, TableInfo.Column>(5);
        _columnsFragments.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFragments.put("subjectId", new TableInfo.Column("subjectId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFragments.put("content", new TableInfo.Column("content", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFragments.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFragments.put("isUnmerged", new TableInfo.Column("isUnmerged", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysFragments = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesFragments = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoFragments = new TableInfo("fragments", _columnsFragments, _foreignKeysFragments, _indicesFragments);
        final TableInfo _existingFragments = TableInfo.read(db, "fragments");
        if (!_infoFragments.equals(_existingFragments)) {
          return new RoomOpenHelper.ValidationResult(false, "fragments(com.lingji.app.data.db.entities.FragmentEntity).\n"
                  + " Expected:\n" + _infoFragments + "\n"
                  + " Found:\n" + _existingFragments);
        }
        final HashMap<String, TableInfo.Column> _columnsNotebookPages = new HashMap<String, TableInfo.Column>(8);
        _columnsNotebookPages.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNotebookPages.put("subjectId", new TableInfo.Column("subjectId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNotebookPages.put("title", new TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNotebookPages.put("content", new TableInfo.Column("content", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNotebookPages.put("orderIndex", new TableInfo.Column("orderIndex", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNotebookPages.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNotebookPages.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNotebookPages.put("indexedAt", new TableInfo.Column("indexedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysNotebookPages = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesNotebookPages = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoNotebookPages = new TableInfo("notebook_pages", _columnsNotebookPages, _foreignKeysNotebookPages, _indicesNotebookPages);
        final TableInfo _existingNotebookPages = TableInfo.read(db, "notebook_pages");
        if (!_infoNotebookPages.equals(_existingNotebookPages)) {
          return new RoomOpenHelper.ValidationResult(false, "notebook_pages(com.lingji.app.data.db.entities.NotebookPageEntity).\n"
                  + " Expected:\n" + _infoNotebookPages + "\n"
                  + " Found:\n" + _existingNotebookPages);
        }
        final HashMap<String, TableInfo.Column> _columnsSettings = new HashMap<String, TableInfo.Column>(6);
        _columnsSettings.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSettings.put("provider", new TableInfo.Column("provider", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSettings.put("baseUrl", new TableInfo.Column("baseUrl", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSettings.put("apiKey", new TableInfo.Column("apiKey", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSettings.put("modelName", new TableInfo.Column("modelName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSettings.put("enableThinking", new TableInfo.Column("enableThinking", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSettings = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSettings = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoSettings = new TableInfo("settings", _columnsSettings, _foreignKeysSettings, _indicesSettings);
        final TableInfo _existingSettings = TableInfo.read(db, "settings");
        if (!_infoSettings.equals(_existingSettings)) {
          return new RoomOpenHelper.ValidationResult(false, "settings(com.lingji.app.data.db.entities.SettingsEntity).\n"
                  + " Expected:\n" + _infoSettings + "\n"
                  + " Found:\n" + _existingSettings);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "8b5254eb161ef4031aee097782f7be37", "533e2d65a4cae8bb0291c11219d6d3c4");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "subjects","fragments","notebook_pages","settings");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `subjects`");
      _db.execSQL("DELETE FROM `fragments`");
      _db.execSQL("DELETE FROM `notebook_pages`");
      _db.execSQL("DELETE FROM `settings`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(SubjectDao.class, SubjectDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(FragmentDao.class, FragmentDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(NotebookPageDao.class, NotebookPageDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(SettingsDao.class, SettingsDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public SubjectDao subjectDao() {
    if (_subjectDao != null) {
      return _subjectDao;
    } else {
      synchronized(this) {
        if(_subjectDao == null) {
          _subjectDao = new SubjectDao_Impl(this);
        }
        return _subjectDao;
      }
    }
  }

  @Override
  public FragmentDao fragmentDao() {
    if (_fragmentDao != null) {
      return _fragmentDao;
    } else {
      synchronized(this) {
        if(_fragmentDao == null) {
          _fragmentDao = new FragmentDao_Impl(this);
        }
        return _fragmentDao;
      }
    }
  }

  @Override
  public NotebookPageDao notebookPageDao() {
    if (_notebookPageDao != null) {
      return _notebookPageDao;
    } else {
      synchronized(this) {
        if(_notebookPageDao == null) {
          _notebookPageDao = new NotebookPageDao_Impl(this);
        }
        return _notebookPageDao;
      }
    }
  }

  @Override
  public SettingsDao settingsDao() {
    if (_settingsDao != null) {
      return _settingsDao;
    } else {
      synchronized(this) {
        if(_settingsDao == null) {
          _settingsDao = new SettingsDao_Impl(this);
        }
        return _settingsDao;
      }
    }
  }
}
