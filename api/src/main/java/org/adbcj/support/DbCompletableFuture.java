package org.adbcj.support;

import org.adbcj.DbCallback;
import org.adbcj.DbException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public final class DbCompletableFuture<T> extends CompletableFuture<T> implements DbCallback<T>{
    private static final Logger logger = LoggerFactory.getLogger(DbCompletableFuture.class);
    @Override
    public void onComplete(T result, DbException failure) {
        if(failure==null){
            if(!complete(result)){
                if(logger.isWarnEnabled()){
                    logger.warn("Tried to complete a already completed future. Tried to complete as success with value []",result);
                }
            }
        } else{
            if(!this.completeExceptionally(failure)){
                if(logger.isWarnEnabled()){
                    logger.warn("Tried to fail a already completed future. Tried to signal failure []",failure);
                }
            }
        }
    }

}
