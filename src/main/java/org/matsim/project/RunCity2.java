package org.matsim.project;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.config.groups.ReplanningConfigGroup;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import static org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists;

/**
 * Runs MATSim with the City_1 / Cottbus configuration (EPSG:25833).
 *
 * Key steps beyond a plain Controler.run():
 *  1. loadScenario — actually reads the network and plans from disk.
 *  2. NetworkUtils.cleanNetwork — removes car-unreachable nodes/links from the
 *     mixed-mode network (walk/car/bike) so routing does not crash.
 *  3. modeVehicleTypesFromVehiclesData + "car" VehicleType — ensures every
 *     car-using person gets a vehicle each iteration, even after SubtourModeChoice.
 *
 * Working directory must be the project root (matsim_forT/).
 */
public class RunCity2 {

    public static void main(String[] args) {

        // ── 1. Load config ────────────────────────────────────────────────
        Config config = ConfigUtils.loadConfig(
                "/Users/zekiaga/Documents/MATSIM/matsim_forT/original-input-data/City_1/configCity1.xml");

        config.controller().setOverwriteFileSetting(deleteDirectoryIfExists);
        config.controller().setLastIteration(5);

        config.qsim().setLinkDynamics(QSimConfigGroup.LinkDynamics.PassingQ);

        // modeVehicleTypesFromVehiclesData: at the start of every iteration
        // MATSim creates one vehicle of type "car" for every person whose
        // current plan has a car leg — including agents newly switched to car
        // by SubtourModeChoice.  This eliminates the deprecated
        // usePersonIdForMissingVehicleId fallback.
        config.qsim().setVehiclesSource(QSimConfigGroup.VehiclesSource.modeVehicleTypesFromVehiclesData);

        // Only car uses the road network; walk / bike / pt are teleported
        // (already declared in configCity1.xml's routing module).
        {
            Collection<String> modes = new ArrayList<>();
            modes.add(TransportMode.car);
            config.routing().setNetworkModes(modes);
        }

        // ── Scoring — mode parameters ─────────────────────────────────────
        // Required for every mode that SubtourModeChoice can assign.
        {
            for (String mode : new String[]{"car", "walk", "bike", "pt"}) {
                config.scoring().addModeParams(new ScoringConfigGroup.ModeParams(mode));
            }
        }

        // ── Replanning ────────────────────────────────────────────────────
        // Modes found in Plans101_city1.xml: car=86, pt=53, walk=41, bike=26.
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

        // ── 2. Load scenario ──────────────────────────────────────────────
        Scenario scenario = ScenarioUtils.loadScenario(config);

        // ── 3. Vehicle type ───────────────────────────────────────────────
        // VehicleType ID must match the mode name ("car").
        {
            VehicleType carType = VehicleUtils.createVehicleType(Id.create("car", VehicleType.class));
            carType.setMaximumVelocity(50.0 / 3.6);   // 50 km/h — Cottbus urban speed
            scenario.getVehicles().addVehicleType(carType);
        }

        // ── 4. Clean the car sub-network ──────────────────────────────────
        // Network_City1.xml has mixed modes (car, walk, bike).
        // cleanNetwork keeps only the largest strongly-connected component
        // for "car", removing nodes reachable only via walk/bike links.
        NetworkUtils.cleanNetwork(scenario.getNetwork(), Set.of(TransportMode.car));

        // ── 5. Run ────────────────────────────────────────────────────────
        Controler controler = new Controler(scenario);
        controler.run();
    }
}
