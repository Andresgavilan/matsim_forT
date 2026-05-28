package org.matsim.project;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.config.groups.ReplanningConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import static org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists;

/**
 * Runs MATSim with the City_1 configuration (Cottbus / EPSG:25833).
 *
 * Key difference from a plain Controler.run(): after loading the scenario the
 * car sub-network is cleaned with NetworkUtils.cleanNetwork() before routing
 * is initialised.  This removes any links / nodes that are unreachable for
 * mode "car" (disconnected islands in the OSM extract) and prevents the
 * "Network for mode 'car' has unreachable links and nodes" crash.
 *
 * IntelliJ Run/Debug Configurations → Working directory MUST be the project
 * root (matsim_forT/), because configCity1.xml uses relative paths.
 */
public class RunCity2 {

    public static void main(String[] args) {

        // ── 1. Load config ────────────────────────────────────────────────
        Config config = ConfigUtils.loadConfig(
                "/Users/zekiaga/Documents/MATSIM/matsim_forT/original-input-data/City_1/configCity1.xml");

        config.controller().setOverwriteFileSetting(deleteDirectoryIfExists);
        config.controller().setLastIteration(5);

        config.qsim().setLinkDynamics(QSimConfigGroup.LinkDynamics.PassingQ);
        config.qsim().setVehiclesSource(QSimConfigGroup.VehiclesSource.defaultVehicle);

        // Only car uses the road network; walk / bike / pt are teleported
        // (already declared in configCity1.xml's routing module).
        {
            Collection<String> modes = new ArrayList<>();
            modes.add(TransportMode.car);
            config.routing().setNetworkModes(modes);
        }

        // SubtourModeChoice — modes present in Plans101_city1.xml:
        //   car=86  pt=53  walk=41  bike=26
        // car is chain-based: agents must return it to where they parked it.
        {
            ReplanningConfigGroup.StrategySettings smc = new ReplanningConfigGroup.StrategySettings();
            smc.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.SubtourModeChoice);
            smc.setWeight(0.3);
            config.replanning().addStrategySettings(smc);

            String[] allModes        = {"car", "pt", "walk", "bike"};
            String[] chainBasedModes = {"car"};
            config.subtourModeChoice().setModes(allModes);
            config.subtourModeChoice().setChainBasedModes(chainBasedModes);
        }

        // ── 2. Load scenario (reads Network_City1.xml + Plans101_city1.xml) ─
        Scenario scenario = ScenarioUtils.loadScenario(config);

        // ── 3. Clean the car sub-network ──────────────────────────────────
        //
        // Network_City1.xml contains links with mixed modes (car, walk, pt).
        // When MATSim isolates the car-only links it finds disconnected islands
        // (nodes reachable only via walk/pt links).  cleanNetwork() keeps only
        // the largest strongly-connected component for "car" and removes the
        // orphan links/nodes, so every origin can reach every destination.
        //
        // This is the approach recommended by the MATSim error message:
        //   "consider doing that with NetworkUtils.restrictModesAndCleanNetwork()"
        NetworkUtils.cleanNetwork(scenario.getNetwork(), Set.of(TransportMode.car));

        // ── 4. Run ────────────────────────────────────────────────────────
        Controler controler = new Controler(scenario);
        controler.run();
    }
}
