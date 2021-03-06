package com.froobworld.saml.tasks;

import com.froobworld.saml.Saml;
import org.bukkit.Bukkit;

public class CacheSavingTask implements Runnable {
    private Saml saml;

    public CacheSavingTask(Saml saml) {
        this.saml = saml;
        if(saml.getMobFreezeTask().getFrozenChunkCache() != null) {
            Bukkit.getScheduler().scheduleSyncRepeatingTask(saml, this, 1200, 1200);
        }
    }

    @Override
    public void run() {
        if(saml.getMobFreezeTask().getFrozenChunkCache().hasUnsavedChanges()) {
            saml.getMobFreezeTask().getFrozenChunkCache().saveToFile();
        }
    }
}
