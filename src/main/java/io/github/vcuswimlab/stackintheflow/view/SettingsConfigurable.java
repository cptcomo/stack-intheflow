package io.github.vcuswimlab.stackintheflow.view;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import io.github.vcuswimlab.stackintheflow.controller.component.PersistSettingsComponent;
import io.github.vcuswimlab.stackintheflow.controller.component.PersistSettingsComponent.SettingKey;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.EnumMap;
import java.util.Map;

/**
 * <h1>SettingsConfigurable</h1>
 * Created on: 7/24/2017
 *
 * @author Tyler John Haden
 */
public class SettingsConfigurable implements Configurable {

    private static final String DISPLAY_NAME = "Stack-InTheFlow";
    private static PersistSettingsComponent persistSettingsComponent;
    private static SettingsGUI settingsGUI;

    @Nls
    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Nullable
    @Override
    public String getHelpTopic() {
        return null;
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        persistSettingsComponent = ServiceManager.getService(PersistSettingsComponent.class);
        settingsGUI = new SettingsGUI();
        return settingsGUI.build(persistSettingsComponent.getSettingsMap());
    }

    @Override
    public void disposeUIResources() {
        settingsGUI = null;
    }

    @Override
    public boolean isModified() {
        Map<SettingKey, Boolean> guiState = settingsGUI.getGUIState();
        Map<SettingKey, Boolean> persistState = persistSettingsComponent.getSettingsMap();
        return !guiState.equals(persistState);
    }

    @Override
    public void apply() throws ConfigurationException {
        Map<SettingKey, Boolean> guiState = settingsGUI.getGUIState();
        persistSettingsComponent.updateSettings(guiState);
    }

    @Override
    public void reset() {
    }
}
