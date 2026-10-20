package com.example.smarthome.service;

import android.content.Context;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.smarthome.data.HomeRepository;
import com.example.smarthome.data.RepositoryCallback;
import com.example.smarthome.data.database.DeviceEntity;
import com.example.smarthome.data.database.RoomEntity;
import com.example.smarthome.data.database.SmartHomeDatabase;
import com.example.smarthome.integration.ControllerGateway;
import com.example.smarthome.model.Device;
import com.example.smarthome.model.LightDevice;
import com.example.smarthome.model.RelayDevice;
import com.example.smarthome.model.Room;
import com.example.smarthome.model.WaterDevice;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Реальная Room в памяти; gateway управляется тестом, оборудование не требуется. */
@RunWith(AndroidJUnit4.class)
public class DeviceControlServiceTest
{
    private SmartHomeDatabase database;
    private HomeRepository repository;
    private FakeGateway gateway;
    private DeviceControlService service;
    private int roomId;

    @Before
    public void setUp()
    {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        database = androidx.room.Room.inMemoryDatabaseBuilder(context, SmartHomeDatabase.class).build();
        repository = new HomeRepository(database);
        gateway = new FakeGateway();
        service = new DeviceControlService(repository, gateway, 2000L, 5000L);
        roomId = (int) database.roomDao().insert(new RoomEntity(0, "Кухня"));
    }

    @After
    public void tearDown() throws Exception
    {
        awaitRepository();
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        database.close();
    }

    private void main(Runnable action)
    {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }

    private void awaitRepository() throws Exception
    {
        final CountDownLatch completed = new CountDownLatch(1);
        repository.getRooms(new RepositoryCallback<List<Room>>()
        {
            @Override
            public void onSuccess(@NonNull List<Room> result)
            {
                completed.countDown();
            }
            @Override
            public void onError(@NonNull Exception error)
            {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private RelayDevice addRelay(boolean online, boolean poweredOn)
    {
        DeviceEntity entity = new DeviceEntity(0, "Реле", roomId, "relay-controller",
                DeviceEntity.TYPE_RELAY, online);
        entity.setPoweredOn(poweredOn);
        int id = (int) database.deviceDao().insert(entity);
        return new RelayDevice(id, entity.getName(), roomId, entity.getControllerId(), online, poweredOn);
    }

    private LightDevice addLight()
    {
        DeviceEntity entity = new DeviceEntity(0, "Свет", roomId, "light-controller",
                DeviceEntity.TYPE_LIGHT, true);
        entity.setPoweredOn(true);
        entity.setBrightness(30);
        entity.setLightTemperature(40);
        int id = (int) database.deviceDao().insert(entity);
        return new LightDevice(id, "Свет", roomId, "light-controller", true, true, 30, 40);
    }

    private WaterDevice addWater(boolean known)
    {
        DeviceEntity entity = new DeviceEntity(0, "Бак", roomId, "water-controller",
                DeviceEntity.TYPE_WATER, true);
        if (known)
        {
            entity.setWaterLevel(60);
            entity.setTemperature(21.5);
            entity.setLevelStatus("Норма");
            entity.setLastUpdate(2000L);
            entity.setValveOpen(true);
        }
        int id = (int) database.deviceDao().insert(entity);
        WaterDevice water = new WaterDevice(id, "Бак", roomId, "water-controller", true);
        if (known)
        {
            water.updateReading(60, 21.5, "Норма", 2000L);
            water.setValveOpen(true);
        }
        return water;
    }

    @Test
    public void relay_success_savesAfterConfirmationAndDoesNotMutateInput() throws Exception
    {
        final RelayDevice relay = addRelay(false, false);
        final Probe<RelayDevice> result = new Probe<RelayDevice>();
        main(new Runnable()
        {
            @Override
            public void run()
            {
                service.setRelayState(relay, true, result);
            }
        });
        assertEquals(Boolean.FALSE, database.deviceDao().getById(relay.getId()).getPoweredOn());
        main(new Runnable()
        {
            @Override
            public void run()
            {
                gateway.answerRelay();
            }
        });
        RelayDevice saved = result.success();
        assertTrue(saved.isOnline());
        assertTrue(saved.isPoweredOn());
        assertFalse(relay.isOnline());
        assertFalse(relay.isPoweredOn());
        assertEquals(Boolean.TRUE, database.deviceDao().getById(relay.getId()).getPoweredOn());
    }

    @Test
    public void relay_duplicateReply_deliversOneResult() throws Exception
    {
        final RelayDevice relay = addRelay(true, false);
        final Probe<RelayDevice> result = new Probe<RelayDevice>();
        main(new Runnable()
        {
            @Override
            public void run()
            {
                service.setRelayState(relay, true, result);
                gateway.answerRelay();
                gateway.answerRelay();
                gateway.relayCallback.onError(gateway.relayRequestId, relay.getId(), new Exception("Поздняя ошибка"));
            }
        });
        result.success();
        awaitRepository();
        assertEquals(1, result.calls.get());
    }

    @Test
    public void relay_wrongRequest_rejectedWithoutSaving() throws Exception
    {
        final RelayDevice relay = addRelay(true, false);
        final Probe<RelayDevice> result = new Probe<RelayDevice>();
        main(new Runnable()
        {
            @Override
            public void run()
            {
                service.setRelayState(relay, true, result);
                gateway.relayCallback.onSuccess(gateway.relayRequestId + 1,
                        new RelayDevice(relay.getId(), "Реле", roomId, "relay-controller", true, true));
            }
        });
        assertTrue(result.failure() instanceof IllegalStateException);
        assertEquals(Boolean.FALSE, database.deviceDao().getById(relay.getId()).getPoweredOn());
    }

    @Test
    public void relay_wrongController_rejectedWithoutSaving() throws Exception
    {
        final RelayDevice relay = addRelay(true, false);
        final Probe<RelayDevice> result = new Probe<RelayDevice>();
        main(new Runnable()
        {
            @Override
            public void run()
            {
                service.setRelayState(relay, true, result);
                gateway.relayCallback.onSuccess(gateway.relayRequestId,
                        new RelayDevice(relay.getId(), "Реле", roomId, "wrong-controller", true, true));
            }
        });
        result.failure();
        assertEquals(Boolean.FALSE, database.deviceDao().getById(relay.getId()).getPoweredOn());
    }

    @Test
    public void relay_timeout_ignoresLateSuccessAndReleasesDevice() throws Exception
    {
        service = new DeviceControlService(repository, gateway, 80L, 5000L);
        final RelayDevice relay = addRelay(true, false);
        final Probe<RelayDevice> first = new Probe<RelayDevice>();
        final Probe<RelayDevice> second = new Probe<RelayDevice>();
        main(new Runnable()
        {
            @Override
            public void run()
            {
                service.setRelayState(relay, true, first);
            }
        });
        assertTrue(first.failure() instanceof TimeoutException);
        main(new Runnable()
        {
            @Override
            public void run()
            {
                gateway.answerRelay();
                service.setRelayState(relay, true, second);
                gateway.answerRelay();
            }
        });
        second.success();
        assertEquals(1, first.calls.get());
    }

    @Test
    public void busyDevice_rejectsSecondCommand_butAllowsAnotherDevice() throws Exception
    {
        final RelayDevice relay = addRelay(true, false);
        final LightDevice light = addLight();
        final Probe<RelayDevice> first = new Probe<RelayDevice>();
        final Probe<RelayDevice> rejected = new Probe<RelayDevice>();
        final Probe<LightDevice> independent = new Probe<LightDevice>();
        main(new Runnable()
        {
            @Override
            public void run()
            {
                service.setRelayState(relay, true, first);
                service.setRelayState(relay, false, rejected);
                service.setLightState(light, false, 77, 45, independent);
                gateway.answerLight();
                gateway.answerRelay();
            }
        });
        assertTrue(rejected.failure() instanceof IllegalStateException);
        first.success();
        independent.success();
        assertEquals(1, gateway.relayCalls);
        assertEquals(1, gateway.lightCalls);
    }

    @Test
    public void relay_deletedAfterCommand_reportsConfirmedStateSaveError() throws Exception
    {
        final RelayDevice relay = addRelay(true, false);
        final Probe<RelayDevice> result = new Probe<RelayDevice>();
        main(new Runnable()
        {
            @Override
            public void run()
            {
                service.setRelayState(relay, true, result);
            }
        });
        database.deviceDao().deleteById(relay.getId());
        main(new Runnable()
        {
            @Override
            public void run()
            {
                gateway.answerRelay();
            }
        });
        Event<RelayDevice> event = result.take();
        assertTrue(event.saveError);
        assertNotNull(event.error);
        assertTrue(event.device.isPoweredOn());
        assertEquals(0, database.deviceDao().getAll().size());
    }

    @Test
    public void light_off_keepsRequestedSettings() throws Exception
    {
        final LightDevice light = addLight();
        final Probe<LightDevice> result = new Probe<LightDevice>();
        main(new Runnable()
        {
            @Override
            public void run()
            {
                service.setLightState(light, false, 77, 45, result);
                gateway.answerLight();
            }
        });
        LightDevice saved = result.success();
        assertFalse(saved.isPoweredOn());
        assertEquals(77, saved.getBrightness());
        assertEquals(45, saved.getLightTemperature());
        assertTrue(light.isPoweredOn());
        assertEquals(30, light.getBrightness());
    }

    @Test
    public void light_invalidPercentage_doesNotContactGateway() throws Exception
    {
        final LightDevice light = addLight();
        final Probe<LightDevice> result = new Probe<LightDevice>();
        main(new Runnable()
        {
            @Override
            public void run()
            {
                service.setLightState(light, true, 101, 50, result);
            }
        });
        assertTrue(result.failure() instanceof IllegalArgumentException);
        assertEquals(0, gateway.lightCalls);
        assertEquals(Integer.valueOf(30), database.deviceDao().getById(light.getId()).getBrightness());
    }

    @Test
    public void light_unconfirmedSettings_notSaved() throws Exception
    {
        final LightDevice light = addLight();
        final Probe<LightDevice> result = new Probe<LightDevice>();
        main(new Runnable()
        {
            @Override
            public void run()
            {
                service.setLightState(light, true, 77, 45, result);
                gateway.lightCallback.onSuccess(gateway.lightRequestId,
                        new LightDevice(light.getId(), "Свет", roomId, "light-controller", true, true, 76, 45));
            }
        });
        result.failure();
        assertEquals(Integer.valueOf(30), database.deviceDao().getById(light.getId()).getBrightness());
    }

    @Test
    public void valve_unknownReadings_preservesNullAndSavesConfirmedValve() throws Exception
    {
        final WaterDevice water = addWater(false);
        final Probe<WaterDevice> result = new Probe<WaterDevice>();
        main(new Runnable()
        {
            @Override
            public void run()
            {
                service.setValveOpen(water, false, result);
                WaterDevice response = new WaterDevice(water.getId(), "Бак", roomId, "water-controller", true);
                response.setValveOpen(false);
                gateway.waterCallback.onSuccess(gateway.waterRequestId, response);
            }
        });
        WaterDevice saved = result.success();
        assertEquals(Boolean.FALSE, saved.getValveOpen());
        assertNull(saved.getWaterLevel());
        assertNull(saved.getLastUpdate());
        assertNull(water.getValveOpen());
    }

    @Test
    public void valve_oldReadings_doNotOverwriteNewerDatabaseMeasurements() throws Exception
    {
        final WaterDevice water = addWater(true);
        final Probe<WaterDevice> result = new Probe<WaterDevice>();
        main(new Runnable()
        {
            @Override
            public void run()
            {
                service.setValveOpen(water, false, result);
                WaterDevice response = new WaterDevice(water.getId(), "Бак", roomId, "water-controller", true);
                response.updateReading(10, 10.0, "Старое", 1000L);
                response.setValveOpen(false);
                gateway.waterCallback.onSuccess(gateway.waterRequestId, response);
            }
        });
        WaterDevice saved = result.success();
        assertEquals(Integer.valueOf(60), saved.getWaterLevel());
        assertEquals(Long.valueOf(2000L), saved.getLastUpdate());
        assertEquals(Boolean.FALSE, saved.getValveOpen());
    }

    @Test
    public void valve_nullConfirmation_isError() throws Exception
    {
        final WaterDevice water = addWater(true);
        final Probe<WaterDevice> result = new Probe<WaterDevice>();
        main(new Runnable()
        {
            @Override
            public void run()
            {
                service.setValveOpen(water, false, result);
                gateway.waterCallback.onSuccess(gateway.waterRequestId,
                        new WaterDevice(water.getId(), "Бак", roomId, "water-controller", true));
            }
        });
        result.failure();
        assertEquals(Boolean.TRUE, database.deviceDao().getById(water.getId()).getValveOpen());
    }

    @Test
    public void read_offline_changesOnlyAvailability() throws Exception
    {
        final LightDevice light = addLight();
        final Probe<Device> result = new Probe<Device>();
        main(new Runnable()
        {
            @Override
            public void run()
            {
                service.readDeviceState(light, result);
                gateway.readCallback.onSuccess(gateway.readRequestId,
                        new LightDevice(light.getId(), "Свет", roomId, "light-controller", false, false, 0, 0));
            }
        });
        LightDevice saved = (LightDevice) result.success();
        assertFalse(saved.isOnline());
        assertTrue(saved.isPoweredOn());
        assertEquals(30, saved.getBrightness());
    }

    @Test
    public void read_newWaterPacket_updatesMeasurements() throws Exception
    {
        final WaterDevice water = addWater(true);
        final Probe<Device> result = new Probe<Device>();
        main(new Runnable()
        {
            @Override
            public void run()
            {
                service.readDeviceState(water, result);
                WaterDevice response = new WaterDevice(water.getId(), "Бак", roomId, "water-controller", true);
                response.updateReading(80, 24.5, "Высокий", 3000L);
                gateway.readCallback.onSuccess(gateway.readRequestId, response);
            }
        });
        WaterDevice saved = (WaterDevice) result.success();
        assertEquals(Integer.valueOf(80), saved.getWaterLevel());
        assertEquals(Long.valueOf(3000L), saved.getLastUpdate());
        assertEquals(Boolean.TRUE, saved.getValveOpen());
    }

    @Test
    public void subscriptions_shareTransportAndCancelIndependently() throws Exception
    {
        final RelayDevice relay = addRelay(true, true);
        final Probe<Device> first = new Probe<Device>();
        final Probe<Device> second = new Probe<Device>();
        final ControllerGateway.Subscription[] handles = new ControllerGateway.Subscription[2];
        main(new Runnable()
        {
            @Override
            public void run()
            {
                handles[0] = service.subscribeToDevice(relay, first);
                handles[1] = service.subscribeToDevice(relay, second);
                handles[0].cancel();
                gateway.listener.onAvailabilityChanged(relay.getId(), false);
            }
        });
        Device result = second.success();
        assertFalse(result.isOnline());
        assertTrue(((RelayDevice) result).isPoweredOn());
        assertEquals(0, first.calls.get());
        assertEquals(1, gateway.subscribeCalls);
        assertEquals(0, gateway.cancelSubscriptionCalls);
        main(new Runnable()
        {
            @Override
            public void run()
            {
                handles[1].cancel();
                handles[1].cancel();
                gateway.listener.onAvailabilityChanged(relay.getId(), true);
            }
        });
        awaitRepository();
        assertEquals(1, gateway.cancelSubscriptionCalls);
        assertEquals(1, second.calls.get());
        assertFalse(database.deviceDao().getById(relay.getId()).isOnline());
    }

    @Test
    public void subscription_transportError_doesNotMarkOffline() throws Exception
    {
        final RelayDevice relay = addRelay(true, true);
        final Probe<Device> result = new Probe<Device>();
        final ControllerGateway.Subscription[] handle = new ControllerGateway.Subscription[1];
        main(new Runnable()
        {
            @Override
            public void run()
            {
                handle[0] = service.subscribeToDevice(relay, result);
                gateway.listener.onError(relay.getId(), new IllegalArgumentException("Повреждён пакет"));
            }
        });
        result.failure();
        assertTrue(database.deviceDao().getById(relay.getId()).isOnline());
        main(new Runnable()
        {
            @Override
            public void run()
            {
                handle[0].cancel();
            }
        });
    }

    @Test
    public void subscription_eventAfterCommandResponse_isSavedAfterCommand() throws Exception
    {
        final RelayDevice relay = addRelay(true, false);
        final Probe<RelayDevice> command = new Probe<RelayDevice>();
        final Probe<Device> events = new Probe<Device>();
        final ControllerGateway.Subscription[] handle = new ControllerGateway.Subscription[1];
        main(new Runnable()
        {
            @Override
            public void run()
            {
                handle[0] = service.subscribeToDevice(relay, events);
                service.setRelayState(relay, true, command);
                gateway.listener.onStateChanged(new RelayDevice(relay.getId(), "Реле", roomId,
                        "relay-controller", true, false));
                gateway.answerRelay();
                gateway.listener.onAvailabilityChanged(relay.getId(), false);
            }
        });
        assertTrue(command.success().isPoweredOn());
        RelayDevice saved = (RelayDevice) events.success();
        assertFalse(saved.isOnline());
        assertTrue(saved.isPoweredOn());
        main(new Runnable()
        {
            @Override
            public void run()
            {
                handle[0].cancel();
            }
        });
    }

    @Test
    public void connection_relay_savesGeneratedIdAndPreservesCredentialsExactly() throws Exception
    {
        final ConnectionProbe result = new ConnectionProbe();
        main(new Runnable()
        {
            @Override
            public void run()
            {
                service.connectDevice("  Реле новое  ", roomId, " My WiFi ", " pass ", result);
            }
        });
        gateway.awaitConnection();
        assertEquals(" My WiFi ", gateway.ssid);
        assertEquals(" pass ", gateway.password);
        assertEquals(0, database.deviceDao().getAll().size());
        main(new Runnable()
        {
            @Override
            public void run()
            {
                gateway.connectionCallback.onProgress(gateway.connectionRequestId,
                        ControllerGateway.ConnectionStage.READING_STATE, null);
                gateway.connectionCallback.onRelayConnected(gateway.connectionRequestId, " new-controller ", true);
                gateway.connectionCallback.onRelayConnected(gateway.connectionRequestId, " new-controller ", true);
            }
        });
        RelayDevice saved = (RelayDevice) result.success();
        assertTrue(saved.getId() > 0);
        assertEquals("Реле новое", saved.getName());
        assertEquals("new-controller", saved.getControllerId());
        assertEquals(roomId, saved.getRoomId());
        assertTrue(saved.isPoweredOn());
        assertEquals(1, database.deviceDao().getAll().size());
        assertEquals(1, result.calls.get());
    }

    @Test
    public void connection_light_savesReportedSettings() throws Exception
    {
        final ConnectionProbe result = new ConnectionProbe();
        startConnection(result);
        main(new Runnable()
        {
            @Override
            public void run()
            {
                gateway.connectionCallback.onLightConnected(gateway.connectionRequestId, "light-new", false, 77, 40);
            }
        });
        LightDevice saved = (LightDevice) result.success();
        assertFalse(saved.isPoweredOn());
        assertEquals(77, saved.getBrightness());
        assertEquals(40, saved.getLightTemperature());
    }

    @Test
    public void connection_water_unknownReadingsRemainNull() throws Exception
    {
        final ConnectionProbe result = new ConnectionProbe();
        startConnection(result);
        main(new Runnable()
        {
            @Override
            public void run()
            {
                gateway.connectionCallback.onWaterConnected(gateway.connectionRequestId,
                        "water-new", null, null, null, null, null);
            }
        });
        WaterDevice saved = (WaterDevice) result.success();
        assertNull(saved.getValveOpen());
        assertNull(saved.getWaterLevel());
        assertNull(saved.getLastUpdate());
    }

    @Test
    public void connection_waterLevelOnly_preservesMissingMeasurements() throws Exception
    {
        final ConnectionProbe result = new ConnectionProbe();
        startConnection(result);
        main(new Runnable()
        {
            @Override public void run()
            {
                gateway.connectionCallback.onWaterConnected(gateway.connectionRequestId,
                        "water-new", 63, null, null, null, null);
            }
        });
        WaterDevice saved = (WaterDevice) result.success();
        assertEquals(Integer.valueOf(63), saved.getWaterLevel());
        assertNull(saved.getTemperature());
        assertNull(saved.getLevelStatus());
        assertNull(saved.getLastUpdate());
    }

    @Test
    public void connection_missingRoom_doesNotContactGateway() throws Exception
    {
        final ConnectionProbe result = new ConnectionProbe();
        main(new Runnable()
        {
            @Override
            public void run()
            {
                service.connectDevice("Реле", 999, "wifi", "", result);
            }
        });
        result.failure();
        assertEquals(0, gateway.connectionCalls);
    }

    @Test
    public void connection_partialWaterPacket_rejectedWithoutInsert() throws Exception
    {
        final ConnectionProbe result = new ConnectionProbe();
        startConnection(result);
        main(new Runnable()
        {
            @Override
            public void run()
            {
                gateway.connectionCallback.onWaterConnected(gateway.connectionRequestId,
                        "water-new", 50, null, "LOW", null, false);
            }
        });
        assertTrue(result.failure() instanceof IllegalArgumentException);
        assertEquals(0, database.deviceDao().getAll().size());
    }

    @Test
    public void connection_invalidProgress_rejectedAndCancelsTransport() throws Exception
    {
        final ConnectionProbe result = new ConnectionProbe();
        startConnection(result);
        main(new Runnable()
        {
            @Override
            public void run()
            {
                gateway.connectionCallback.onProgress(gateway.connectionRequestId,
                        ControllerGateway.ConnectionStage.CONNECTING_TO_NETWORK, 101);
            }
        });
        result.failure();
        assertEquals(1, gateway.cancelConnectionCalls);
        assertEquals(0, database.deviceDao().getAll().size());
    }

    @Test
    public void connection_cancel_ignoresLateSuccess() throws Exception
    {
        final ConnectionProbe result = new ConnectionProbe();
        final ControllerGateway.ConnectionAttempt[] handle = new ControllerGateway.ConnectionAttempt[1];
        main(new Runnable()
        {
            @Override
            public void run()
            {
                handle[0] = service.connectDevice("Реле", roomId, "wifi", "", result);
            }
        });
        gateway.awaitConnection();
        main(new Runnable()
        {
            @Override
            public void run()
            {
                handle[0].cancel();
                handle[0].cancel();
                gateway.connectionCallback.onRelayConnected(gateway.connectionRequestId, "new", true);
            }
        });
        awaitRepository();
        assertEquals(0, result.calls.get());
        assertEquals(0, database.deviceDao().getAll().size());
        assertEquals(1, gateway.cancelConnectionCalls);
    }

    @Test
    public void connection_cancelDuringDuplicateCheck_doesNotInsert() throws Exception
    {
        final ConnectionProbe result = new ConnectionProbe();
        final ControllerGateway.ConnectionAttempt[] handle = new ControllerGateway.ConnectionAttempt[1];
        main(new Runnable()
        {
            @Override
            public void run()
            {
                handle[0] = service.connectDevice("Реле", roomId, "wifi", "", result);
            }
        });
        gateway.awaitConnection();
        main(new Runnable()
        {
            @Override
            public void run()
            {
                gateway.connectionCallback.onRelayConnected(gateway.connectionRequestId, "new", true);
                handle[0].cancel();
            }
        });
        awaitRepository();
        assertEquals(0, result.calls.get());
        assertEquals(0, database.deviceDao().getAll().size());
    }

    @Test
    public void connection_cancelDuringSave_suppressesResultWithoutUndoingInsert() throws Exception
    {
        final ConnectionProbe result = new ConnectionProbe();
        final ControllerGateway.ConnectionAttempt[] handle = new ControllerGateway.ConnectionAttempt[1];
        final CountDownLatch saveStarted = new CountDownLatch(1);
        repository = new HomeRepository(database)
        {
            @Override
            public void addRelayDevice(@NonNull String name, int roomId,
                                       @NonNull String controllerId, boolean online,
                                       boolean poweredOn,
                                       @NonNull RepositoryCallback<RelayDevice> callback)
            {
                // Отмена именно после постановки записи в очередь БД, а не во время
                // добавленной проверки дубликатов. Callback ещё не успел прийти на main.
                super.addRelayDevice(name, roomId, controllerId, online, poweredOn, callback);
                handle[0].cancel();
                saveStarted.countDown();
            }
        };
        service = new DeviceControlService(repository, gateway, 2000L, 5000L);
        main(new Runnable()
        {
            @Override
            public void run()
            {
                handle[0] = service.connectDevice("Реле", roomId, "wifi", "", result);
            }
        });
        gateway.awaitConnection();
        main(new Runnable()
        {
            @Override
            public void run()
            {
                gateway.connectionCallback.onRelayConnected(gateway.connectionRequestId, "new", true);
            }
        });
        assertTrue(saveStarted.await(5, TimeUnit.SECONDS));
        awaitRepository();
        assertEquals(0, result.calls.get());
        assertEquals(1, database.deviceDao().getAll().size());
    }

    @Test
    public void connection_parallelAttempt_rejectedWithoutCancellingFirst() throws Exception
    {
        final ConnectionProbe first = new ConnectionProbe();
        final ConnectionProbe second = new ConnectionProbe();
        startConnection(first);
        main(new Runnable()
        {
            @Override
            public void run()
            {
                service.connectDevice("Другой", roomId, "wifi", "", second);
            }
        });
        second.failure();
        assertEquals(0, gateway.cancelConnectionCalls);
        main(new Runnable()
        {
            @Override
            public void run()
            {
                gateway.connectionCallback.onRelayConnected(gateway.connectionRequestId, "new", false);
            }
        });
        first.success();
        assertEquals(1, gateway.connectionCalls);
    }

    @Test
    public void connection_roomDeleted_reportsSaveFailureWithInfo() throws Exception
    {
        final ConnectionProbe result = new ConnectionProbe();
        startConnection(result);
        database.roomDao().deleteById(roomId);
        main(new Runnable()
        {
            @Override
            public void run()
            {
                gateway.connectionCallback.onRelayConnected(gateway.connectionRequestId, "new", true);
            }
        });
        Event<Device> event = result.take();
        assertTrue(event.saveError);
        assertNotNull(event.error);
        assertNull(event.device);
        assertNotNull(result.info);
        assertEquals("relay", result.info.getType());
        assertEquals(Boolean.TRUE, result.info.getPoweredOn());
    }

    @Test
    public void connection_timeout_handlesReentrantErrorFromCancel() throws Exception
    {
        service = new DeviceControlService(repository, gateway, 2000L, 300L);
        gateway.errorOnCancel = true;
        final ConnectionProbe result = new ConnectionProbe();
        startConnection(result);
        assertTrue(result.failure() instanceof TimeoutException);
        awaitRepository();
        assertEquals(1, gateway.cancelConnectionCalls);
        assertEquals(1, result.calls.get());
    }

    @Test(expected = IllegalStateException.class)
    public void service_rejectsWorkerThreadCalls()
    {
        service.setRelayState(addRelay(true, false), true, new Probe<RelayDevice>());
    }

    private void startConnection(final ConnectionProbe callback) throws Exception
    {
        main(new Runnable()
        {
            @Override
            public void run()
            {
                service.connectDevice("Новое", roomId, "wifi", "", callback);
            }
        });
        gateway.awaitConnection();
    }

    private static class Event<T extends Device>
    {
        T device;
        Exception error;
        boolean saveError;
        boolean mainThread;
    }

    private static class Probe<T extends Device> implements DeviceControlService.Callback<T>
    {
        final BlockingQueue<Event<T>> results = new LinkedBlockingQueue<Event<T>>();
        final AtomicInteger calls = new AtomicInteger();

        void record(T device, Exception error, boolean saveError)
        {
            Event<T> event = new Event<T>();
            event.device = device;
            event.error = error;
            event.saveError = saveError;
            event.mainThread = Looper.myLooper() == Looper.getMainLooper();
            calls.incrementAndGet();
            results.add(event);
        }
        @Override
        public void onSuccess(T device)
        {
            record(device, null, false);
        }
        @Override
        public void onError(Exception error)
        {
            record(null, error, false);
        }
        @Override
        public void onStateSaveError(T device, Exception error)
        {
            record(device, error, true);
        }

        Event<T> take() throws Exception
        {
            Event<T> event = results.poll(5, TimeUnit.SECONDS);
            assertNotNull("Не получен результат сервиса", event);
            assertTrue("Callback должен быть в главном потоке", event.mainThread);
            return event;
        }
        T success() throws Exception
        {
            Event<T> event = take();
            assertNull(event.error);
            assertFalse(event.saveError);
            assertNotNull(event.device);
            return event.device;
        }
        Exception failure() throws Exception
        {
            Event<T> event = take();
            assertNotNull(event.error);
            assertFalse(event.saveError);
            return event.error;
        }
    }

    private static class ConnectionProbe extends Probe<Device>
            implements DeviceControlService.ConnectionCallback
    {
        DeviceControlService.ConnectionInfo info;
        @Override
        public void onProgress(ControllerGateway.ConnectionStage stage, Integer percent)
        {
            // Конечный результат проверяется отдельно от промежуточного прогресса.
        }
        @Override
        public void onStateSaveError(DeviceControlService.ConnectionInfo info, Exception error)
        {
            this.info = info;
            record(null, error, true);
        }
    }

    /** Только тестовый управляемый транспорт: сам команды не выполняет. */
    private static class FakeGateway implements ControllerGateway
    {
        long relayRequestId;
        long lightRequestId;
        long waterRequestId;
        long readRequestId;
        long connectionRequestId;
        int relayCalls;
        int lightCalls;
        int connectionCalls;
        int subscribeCalls;
        int cancelSubscriptionCalls;
        int cancelConnectionCalls;
        boolean relayPower;
        boolean lightPower;
        int brightness;
        int lightTemperature;
        boolean errorOnCancel;
        String ssid;
        String password;
        RelayDevice relay;
        LightDevice light;
        Callback<RelayDevice> relayCallback;
        Callback<LightDevice> lightCallback;
        Callback<WaterDevice> waterCallback;
        Callback<Device> readCallback;
        DeviceListener listener;
        ConnectionCallback connectionCallback;
        final CountDownLatch connectionStarted = new CountDownLatch(1);

        @Override
        public void readDeviceState(long requestId, Device device, Callback<Device> callback)
        {
            readRequestId = requestId;
            readCallback = callback;
        }
        @Override
        public void setRelayState(long requestId, RelayDevice device, boolean poweredOn, Callback<RelayDevice> callback)
        {
            relayCalls++;
            relayRequestId = requestId;
            relay = device;
            relayPower = poweredOn;
            relayCallback = callback;
        }
        @Override
        public void setLightState(long requestId, LightDevice device, boolean poweredOn,
                                  int brightness, int lightTemperature, Callback<LightDevice> callback)
        {
            lightCalls++;
            lightRequestId = requestId;
            light = device;
            lightPower = poweredOn;
            this.brightness = brightness;
            this.lightTemperature = lightTemperature;
            lightCallback = callback;
        }
        @Override
        public void setValveOpen(long requestId, WaterDevice device, boolean valveOpen, Callback<WaterDevice> callback)
        {
            waterRequestId = requestId;
            waterCallback = callback;
        }
        void answerRelay()
        {
            relayCallback.onSuccess(relayRequestId, new RelayDevice(relay.getId(), relay.getName(),
                    relay.getRoomId(), relay.getControllerId(), true, relayPower));
        }
        void answerLight()
        {
            lightCallback.onSuccess(lightRequestId, new LightDevice(light.getId(), light.getName(),
                    light.getRoomId(), light.getControllerId(), true, lightPower, brightness, lightTemperature));
        }
        @Override
        public Subscription subscribeToDevice(Device device, DeviceListener listener)
        {
            subscribeCalls++;
            this.listener = listener;
            return new Subscription()
            {
                @Override
                public void cancel()
                {
                    cancelSubscriptionCalls++;
                }
            };
        }
        @Override
        public ConnectionAttempt connectDevice(long requestId, String ssid, String password, ConnectionCallback callback)
        {
            connectionCalls++;
            connectionRequestId = requestId;
            this.ssid = ssid;
            this.password = password;
            connectionCallback = callback;
            connectionStarted.countDown();
            return new ConnectionAttempt()
            {
                @Override
                public void cancel()
                {
                    cancelConnectionCalls++;
                    if (errorOnCancel)
                    {
                        connectionCallback.onError(connectionRequestId, new IllegalStateException("Отмена"));
                    }
                }
            };
        }
        void awaitConnection() throws Exception
        {
            assertTrue("Gateway не получил запрос подключения", connectionStarted.await(5, TimeUnit.SECONDS));
        }
    }
}
