package com.lingji.app.data.db.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.lingji.app.data.db.entities.SettingsEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class SettingsDao_Impl implements SettingsDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<SettingsEntity> __insertionAdapterOfSettingsEntity;

  public SettingsDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfSettingsEntity = new EntityInsertionAdapter<SettingsEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `settings` (`id`,`provider`,`baseUrl`,`apiKey`,`modelName`,`enableThinking`) VALUES (?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SettingsEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getProvider());
        statement.bindString(3, entity.getBaseUrl());
        statement.bindString(4, entity.getApiKey());
        statement.bindString(5, entity.getModelName());
        final int _tmp = entity.getEnableThinking() ? 1 : 0;
        statement.bindLong(6, _tmp);
      }
    };
  }

  @Override
  public Object upsert(final SettingsEntity settings,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfSettingsEntity.insert(settings);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<SettingsEntity> getSettings() {
    final String _sql = "SELECT * FROM settings WHERE id = 'singleton' LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"settings"}, new Callable<SettingsEntity>() {
      @Override
      @Nullable
      public SettingsEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfProvider = CursorUtil.getColumnIndexOrThrow(_cursor, "provider");
          final int _cursorIndexOfBaseUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "baseUrl");
          final int _cursorIndexOfApiKey = CursorUtil.getColumnIndexOrThrow(_cursor, "apiKey");
          final int _cursorIndexOfModelName = CursorUtil.getColumnIndexOrThrow(_cursor, "modelName");
          final int _cursorIndexOfEnableThinking = CursorUtil.getColumnIndexOrThrow(_cursor, "enableThinking");
          final SettingsEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpProvider;
            _tmpProvider = _cursor.getString(_cursorIndexOfProvider);
            final String _tmpBaseUrl;
            _tmpBaseUrl = _cursor.getString(_cursorIndexOfBaseUrl);
            final String _tmpApiKey;
            _tmpApiKey = _cursor.getString(_cursorIndexOfApiKey);
            final String _tmpModelName;
            _tmpModelName = _cursor.getString(_cursorIndexOfModelName);
            final boolean _tmpEnableThinking;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfEnableThinking);
            _tmpEnableThinking = _tmp != 0;
            _result = new SettingsEntity(_tmpId,_tmpProvider,_tmpBaseUrl,_tmpApiKey,_tmpModelName,_tmpEnableThinking);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getSettingsOnce(final Continuation<? super SettingsEntity> $completion) {
    final String _sql = "SELECT * FROM settings WHERE id = 'singleton' LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<SettingsEntity>() {
      @Override
      @Nullable
      public SettingsEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfProvider = CursorUtil.getColumnIndexOrThrow(_cursor, "provider");
          final int _cursorIndexOfBaseUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "baseUrl");
          final int _cursorIndexOfApiKey = CursorUtil.getColumnIndexOrThrow(_cursor, "apiKey");
          final int _cursorIndexOfModelName = CursorUtil.getColumnIndexOrThrow(_cursor, "modelName");
          final int _cursorIndexOfEnableThinking = CursorUtil.getColumnIndexOrThrow(_cursor, "enableThinking");
          final SettingsEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpProvider;
            _tmpProvider = _cursor.getString(_cursorIndexOfProvider);
            final String _tmpBaseUrl;
            _tmpBaseUrl = _cursor.getString(_cursorIndexOfBaseUrl);
            final String _tmpApiKey;
            _tmpApiKey = _cursor.getString(_cursorIndexOfApiKey);
            final String _tmpModelName;
            _tmpModelName = _cursor.getString(_cursorIndexOfModelName);
            final boolean _tmpEnableThinking;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfEnableThinking);
            _tmpEnableThinking = _tmp != 0;
            _result = new SettingsEntity(_tmpId,_tmpProvider,_tmpBaseUrl,_tmpApiKey,_tmpModelName,_tmpEnableThinking);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
