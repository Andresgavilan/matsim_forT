package org.matsim.project;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.config.groups.ReplanningConfigGroup;
import org.matsim.core.config.groups.RoutingConfigGroup;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

import java.util.ArrayList;
import java.util.Collection;

import static org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists;

/**
 * Runs MATSim with the City_2 / Uruguay configuration (EPSG:5379, Bahía Blanca).
 *
 * Working directory must be the project root (matsim_forT/).
 */
public class RunCity1 {

    public static void main(String[] args) {

        // ── 1. Load config ────────────────────────────────────────────────
        Config config = ConfigUtils.loadConfig(
                "/Users/zekiaga/Documents/MATSIM/matsim_forT/original-input-data/cofigcity2.xml");

        config.controller().setOverwriteFileSetting(deleteDirectoryIfExists);
        config.controller().setLastIteration(5);

        config.qsim().setLinkDynamics(QSimConfigGroup.LinkDynamics.PassingQ);

        // modeVehicleTypesFromVehiclesData: at the start of every iteration
        // MATSim creates one vehicle of type "car" for every person whose
        // current plan has a car leg — including agents newly switched to car
        // by SubtourModeChoice.  This eliminates the deprecated
        // usePersonIdForMissingVehicleId fallback.
        config.qsim().setVehiclesSource(QSimConfigGroup.VehiclesSource.modeVehicleTypesFromVehiclesData);

        // ── Routing ───────────────────────────────────────────────────────
        // car uses the road network; walk / bike / pt are teleported.
        // cofigcity2.xml has no <routing> module, so speeds are set here.
        {
            Collection<String> networkModes = new ArrayList<>();
            networkModes.add(TransportMode.car);
            config.routing().setNetworkModes(networkModes);

            RoutingConfigGroup.TeleportedModeParams walk = new RoutingConfigGroup.TeleportedModeParams(TransportMode.walk);
            walk.setTeleportedModeSpeed(1.4);   // m/s  (~5 km/h)
            config.routing().addTeleportedModeParams(walk);

            RoutingConfigGroup.TeleportedModeParams bike = new RoutingConfigGroup.TeleportedModeParams(TransportMode.bike);
            bike.setTeleportedModeSpeed(4.0);   // m/s  (~14 km/h)
            config.routing().addTeleportedModeParams(bike);

            RoutingConfigGroup.TeleportedModeParams pt = new RoutingConfigGroup.TeleportedModeParams(TransportMode.pt);
            pt.setTeleportedModeSpeed(8.0);     // m/s  (~29 km/h)
            config.routing().addTeleportedModeParams(pt);
        }

        // ── Scoring — mode parameters ─────────────────────────────────────
        // Required for every mode that SubtourModeChoice can assign.
        {
            for (String mode : new String[]{"car", "walk", "bike", "pt"}) {
                config.scoring().addModeParams(new ScoringConfigGroup.ModeParams(mode));
            }
        }

        // ── Replanning ────────────────────────────────────────────────────
        {
            ReplanningConfigGroup.StrategySettings settings = new ReplanningConfigGroup.StrategySettings();
            settings.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.SubtourModeChoice);
            settings.setWeight(0.3);
            config.replanning().addStrategySettings(settings);
        }

        // All modes present in PLANS_UY1.xml: car=91, pt=52, walk=35, bike=35.
        // car is chain-based: agents must return it to where they parked it.
        {
            String[] allModes        = {"car", "pt", "walk", "bike"};
            String[] chainBasedModes = {"car"};
            config.subtourModeChoice().setModes(allModes);
            config.subtourModeChoice().setChainBasedModes(chainBasedModes);
        }

        // ── 2. Load scenario ──────────────────────────────────────────────
        // loadScenario (not createScenario) actually reads the network and
        // plans files declared in cofigcity2.xml from disk.
        Scenario scenario = ScenarioUtils.loadScenario(config);

        // ── 3. Vehicle type ───────────────────────────────────────────────
        // VehicleType ID must match the mode name ("car").
        // MATSim uses this type to create one vehicle per car-using person
        // at the start of each iteration.
        {
            VehicleType carType = VehicleUtils.createVehicleType(Id.create("car", VehicleType.class));
            carType.setMaximumVelocity(25.0 / 3.6);   // ~90 km/h — Uruguay road speed
            scenario.getVehicles().addVehicleType(carType);
        }

        // ── 4. Run ────────────────────────────────────────────────────────
        Controler controler = new Controler(scenario);
        controler.run();
    }
}
