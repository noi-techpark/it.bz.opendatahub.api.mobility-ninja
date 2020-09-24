-- OPEN DATA WHERE CLAUSE FOR THE GUEST ACCOUNT
(
    -- station types that are always open, regardless of the origin
	s.stationtype in (
		'NOI-Place',
		'CreativeIndustry',
		'BluetoothStation',
		'CarpoolingHub',
		'CarpoolingService',
		'CarpoolingUser',
		'CarsharingCar',
		'CarsharingStation',
		'EChargingPlug',
		'EChargingStation',
		'Streetstation')

    -- station types that are only partly open, constrained by the origin
	or (s.stationtype = 'Bicycle' and s.origin in ('ALGORAB', 'BIKE_SHARING_MERANO'))
	or (s.stationtype = 'BikesharingStation' and s.origin in ('ALGORAB'))
	or (s.stationtype = 'EnvironmentStation' and s.origin in ('APPATN-open'))
	or (s.stationtype = 'LinkStation' and (s.origin is null or s.origin in ('NOI')))
	or (s.stationtype = 'MeteoStation' and s.origin in ('meteotrentino', 'SIAG'))
	or (s.stationtype = 'ParkingStation' and s.origin in ('FAMAS', 'FBK', 'Municipality Merano'))
	or (s.stationtype = 'RWISstation' and s.origin in ('InfoMobility'))

    -- special rules
	or (s.origin = 'APPABZ' and me.period = 3600)
) --blabla
--123
