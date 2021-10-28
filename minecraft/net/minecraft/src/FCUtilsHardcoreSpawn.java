// FCMOD

package net.minecraft.src;

import java.util.ArrayList;

import net.minecraft.server.MinecraftServer;

public class FCUtilsHardcoreSpawn
{
	// Respawn Radius
	private static final double m_dRadiusBase = HCSCMod.maxRadius;
	private static final double m_dRadiusBaseExclusionDistance = HCSCMod.minRadius;
	
	// Respawn Multiplier
	private static final double m_dNetherRadiusMultiplier = HCSCMod.netherMultiplier;
	private static final double m_dWitherRadiusMultiplier = HCSCMod.witherMultiplier;
	private static final double m_dEndRadiusMultiplier = HCSCMod.endMultiplier;
	private static final double m_dLargeBiomesRadiusMultiplier = HCSCMod.largeMultiplier;
	
	// Respawn Behaviour
	public static final int m_iHardcoreSpawnTimeBetweenReassignments = HCSCMod.maxSpawnTime;
	private static final double m_dRadiusBaseMultipleRespawn = HCSCMod.quickRespawnRadius;
	private static final int m_iMultipleRespawnHealth = HCSCMod.quickRespawnHealth;
	private static final int m_iMultipleRespawnMinFood = HCSCMod.quickRespawnMinFood;
	private static final int m_iMultipleRespawnFoodDecrement = HCSCMod.quickRespawnFoodDecrement;
	private static final int m_iHCSoulMatingProgression = HCSCMod.hcSoulMating;
	
	// Structure Generation
	private final static double m_dRadiusBaseVillageAbandoned = HCSCMod.abandonedVillageRadius;
	private final static double m_dRadiusBaseVillagePartiallyAbandoned = HCSCMod.partiallyAbandonedRadius;
	private final static double m_dRadiusBaseLootedTemple = HCSCMod.lootedTempleRadius;


	public static ArrayList<BiomeGenBase> blacklistedBiomes = new ArrayList();
	private static final int numberOfBiomeSpawnAttempts = 5;
	
	static {
		blacklistedBiomes.add(BiomeGenBase.jungle);
		blacklistedBiomes.add(BiomeGenBase.jungleHills);
		blacklistedBiomes.add(BiomeGenBase.ocean);
	}

	/** 
	 * player respawn maximum radius from original spawn
	 */
	public static double GetPlayerSpawnRadius()
	{
		return m_dRadiusBase * GetWorldTypeRadiusMultiplier() * GetGameProgressRadiusMultiplier();
	}

	/** 
	 * player respawn minimum radius from original spawn
	 */
	public static double GetPlayerSpawnExclusionRadius()
	{
		return m_dRadiusBaseExclusionDistance * GetWorldTypeRadiusMultiplier();
	}

	/** 
	 * specifies the radius in which a player will respawn, if returning to the same spawn point after multiple deaths
	 */	
	public static double GetPlayerMultipleRespawnRadius()
	{
		return m_dRadiusBaseMultipleRespawn;
	}

	public static double GetAbandonedVillageRadius()
	{
		return m_dRadiusBaseVillageAbandoned * GetWorldTypeRadiusMultiplier();
	}

	public static double GetPartiallyAbandonedVillageRadius()
	{
		return m_dRadiusBaseVillagePartiallyAbandoned * GetWorldTypeRadiusMultiplier();
	}

	public static double GetLootedTempleRadius()
	{
		return m_dRadiusBaseLootedTemple * GetWorldTypeRadiusMultiplier();
	}

	public static double GetWorldTypeRadiusMultiplier()
	{
		World overWorld = MinecraftServer.getServer().worldServers[0];

		if ( overWorld != null && overWorld.worldInfo.getTerrainType() == WorldType.LARGE_BIOMES )
		{
			return m_dLargeBiomesRadiusMultiplier;
		}

		return 1D;
	}

	public static double GetGameProgressRadiusMultiplier()
	{
		if ( FCUtilsWorld.GameProgressHasEndDimensionBeenAccessedServerOnly() )
		{
			return m_dEndRadiusMultiplier;
		}
		else if ( FCUtilsWorld.GameProgressHasWitherBeenSummonedServerOnly() )
		{
			return m_dWitherRadiusMultiplier;
		}
		else if ( FCUtilsWorld.GameProgressHasNetherBeenAccessedServerOnly() )
		{
			return m_dNetherRadiusMultiplier;
		}

		return 1D;
	}

	public static void HandleHardcoreSpawn( MinecraftServer server, EntityPlayerMP oldPlayer, EntityPlayerMP newPlayer )
	{
		WorldServer newWorld = server.worldServerForDimension( newPlayer.dimension );

		if ( oldPlayer.playerConqueredTheEnd )
		{
			ReturnPlayerToOriginalSpawn( newWorld, newPlayer );

			return;
		}

		long lOverworldTime = FCUtilsWorld.GetOverworldTimeServerOnly();
		long lTimeOfLastPlayerSpawnAssignment = oldPlayer.m_lTimeOfLastSpawnAssignment;
		long lDeltaTimeSinceLastRespawnAssignment = lOverworldTime - lTimeOfLastPlayerSpawnAssignment; 

		boolean bSoftRespawn = false;

		if ( lTimeOfLastPlayerSpawnAssignment > 0 && lDeltaTimeSinceLastRespawnAssignment >= 0 &&  
				lDeltaTimeSinceLastRespawnAssignment < m_iHardcoreSpawnTimeBetweenReassignments )
		{
			// multiple respawns in a short period of time results in different behavior

			bSoftRespawn = true;

			newPlayer.health = m_iMultipleRespawnHealth; // start the player hurt

			int iFoodLevel = oldPlayer.foodStats.getFoodLevel();

			iFoodLevel -= m_iMultipleRespawnFoodDecrement; // knock off a 1 pip food penalty

			if ( iFoodLevel < m_iMultipleRespawnMinFood )
			{
				iFoodLevel = m_iMultipleRespawnMinFood;
			}

			newPlayer.foodStats.setFoodLevel( iFoodLevel );            
		}
		
		int progression = 0;
		if (FCUtilsWorld.GameProgressHasEndDimensionBeenAccessedServerOnly()) {
    		progression = 3;
    	} else if (FCUtilsWorld.GameProgressHasWitherBeenSummonedServerOnly()) {
    		progression = 2;
    	} else if (FCUtilsWorld.GameProgressHasNetherBeenAccessedServerOnly()) {
    		progression = 1;
    	}

		if ( progression < m_iHCSoulMatingProgression || FCBetterThanWolves.alwaysSpawnTogether )
		{
			// early game, players are tied to respawning together

			FCSpawnLocation recentLocation = newWorld.GetSpawnLocationList().GetMostRecentSpawnLocation();

			if ( recentLocation != null )
			{
				long lDeltaTime = lOverworldTime - recentLocation.m_lSpawnTime;

				if ( lDeltaTime > 0 && lDeltaTime < FCUtilsHardcoreSpawn.m_iHardcoreSpawnTimeBetweenReassignments )
				{
					if ( AssignPlayerToOldSpawnPosWithVariance( newWorld, newPlayer,  
							new ChunkCoordinates( recentLocation.m_iIPos, recentLocation.m_iJPos, recentLocation.m_iKPos ), 
							recentLocation.m_lSpawnTime ) )
					{
						return;
					}
				}
			}
		}

		ChunkCoordinates oldSpawnPos = oldPlayer.m_HardcoreSpawnChunk;		

		// if a day has passed since the last spawn assignment, assign a new one

		if ( oldSpawnPos == null || !bSoftRespawn || 
				!AssignPlayerToOldSpawnPosWithVariance( newWorld, newPlayer, oldSpawnPos, lTimeOfLastPlayerSpawnAssignment ) )
		{
			if ( !AssignNewHardcoreSpawnLocation( newWorld, server, newPlayer ) )
			{
				ReturnPlayerToOriginalSpawn( newWorld, newPlayer );

				return;
			}
		}

		ChunkCoordinates newSpawnPos = newPlayer.m_HardcoreSpawnChunk;

		if ( newSpawnPos != null )
		{
			newWorld.GetSpawnLocationList().AddPointIfNotAlreadyPresent( newSpawnPos.posX, newSpawnPos.posY, newSpawnPos.posZ, newPlayer.m_lTimeOfLastSpawnAssignment );
		}
	}

	public static boolean AssignNewHardcoreSpawnLocation( World world, MinecraftServer server, EntityPlayerMP player )
	{    	
		boolean bLocationFound = false;

		double dSpawnRadius = FCUtilsHardcoreSpawn.GetPlayerSpawnRadius();
		double dSpawnExclusionRadius = FCUtilsHardcoreSpawn.GetPlayerSpawnExclusionRadius();
		double dSpawnDeltaRadius = dSpawnRadius - dSpawnExclusionRadius;

		double dExclusionRadiusSq = dSpawnExclusionRadius * dSpawnExclusionRadius;
		double dDeltaSquaredRadii = ( dSpawnRadius * dSpawnRadius ) - dExclusionRadiusSq; 

		for ( int iAttemptCount = 0; iAttemptCount < 20; iAttemptCount++ )
		{
			// distance used formula: dist = sqrt(rnd()*(R2^2-R1^2)+R1^2) to obtain even distribution
			// The shape involved here (2D doughnut) is called an 'Annulus' 
			double dSpawnDistance = Math.sqrt( world.rand.nextDouble() * dDeltaSquaredRadii + dExclusionRadiusSq );

			double dSpawnYaw = world.rand.nextDouble() * Math.PI * 2D;

			double dXOffset = -Math.sin( dSpawnYaw ) * dSpawnDistance; 
			double dZOffset = Math.cos( dSpawnYaw ) * dSpawnDistance;

			int newSpawnX = MathHelper.floor_double( dXOffset ) + world.worldInfo.getSpawnX();
			int newSpawnZ = MathHelper.floor_double( dZOffset ) + world.worldInfo.getSpawnZ();

			int newSpawnY = world.getTopSolidOrLiquidBlock( newSpawnX, newSpawnZ );
			
			BiomeGenBase respawnBiome = world.getBiomeGenForCoords(newSpawnX, newSpawnZ);
			
			if (blacklistedBiomes.contains(respawnBiome)) {
				//TODO: needs more thought than a simple bail out for all cases
				//continue;
			}

			if ( newSpawnY >= world.provider.getAverageGroundLevel() )
			{
				Material targetMaterial = world.getBlockMaterial( newSpawnX, newSpawnY, newSpawnZ );

				if ( targetMaterial == null || !targetMaterial.isLiquid() )
				{	        	
					player.setLocationAndAngles( (double)newSpawnX + 0.5D, (double)newSpawnY + 1.5D, (double)newSpawnZ + 0.5D, world.rand.nextFloat() * 360F, 0.0F);

					BumpPlayerPosUpwardsUntilValidSpawnReached( player );		        	

					long lOverworldTime = FCUtilsWorld.GetOverworldTimeServerOnly();	        		

					if ( FCBetterThanWolves.IsSinglePlayerNonLan() || MinecraftServer.getServer().getCurrentPlayerCount() == 1 )
					{
						// set the time to the next morning if this is single player

						lOverworldTime = ( ( lOverworldTime / 24000L ) + 1 ) * 24000L;

						for ( int iTempCount = 0; iTempCount < MinecraftServer.getServer().worldServers.length; ++iTempCount)
						{
							WorldServer tempServer = MinecraftServer.getServer().worldServers[iTempCount];

							tempServer.setWorldTime( lOverworldTime );

							if ( tempServer.worldInfo.isThundering() )
							{
								// remove any storms

								tempServer.worldInfo.setThundering( false );

								server.getConfigurationManager().sendPacketToAllPlayers( new Packet70GameEvent( 8, 0 ) );
							}
						}	        	        
					}

					player.m_lTimeOfLastSpawnAssignment = lOverworldTime;

					ChunkCoordinates newSpawnPos = new ChunkCoordinates( MathHelper.floor_double( player.posX ),
							MathHelper.floor_double( player.posY ), MathHelper.floor_double( player.posZ ) );

					player.m_HardcoreSpawnChunk = newSpawnPos;

					bLocationFound = true;

					break;
				}
			}
		}

		return bLocationFound;
	}

	private static boolean AssignPlayerToOldSpawnPosWithVariance( World world, EntityPlayerMP player, ChunkCoordinates spawnPos, long lTimeOfLastPlayerSpawnAssignment )
	{    	
		for ( int iAttemptCount = 0; iAttemptCount < 20; iAttemptCount++ )
		{
			// square root is used on distance to get an even distriubtion of points at any circumference,
			// with more points as you move further from the origin to compensate for increased circumference

			double dSpawnDistance = Math.sqrt( world.rand.nextDouble() ) * GetPlayerMultipleRespawnRadius();

			double dSpawnYaw = world.rand.nextDouble() * Math.PI * 2D;

			double dXOffset = -Math.sin( dSpawnYaw ) * dSpawnDistance; 
			double dZOffset = Math.cos( dSpawnYaw ) * dSpawnDistance;

			int iNewSpawnI = MathHelper.floor_double( dXOffset ) + spawnPos.posX;
			int iNewSpawnK = MathHelper.floor_double( dZOffset ) + spawnPos.posZ;

			int iNewSpawnJ = world.getTopSolidOrLiquidBlock( iNewSpawnI, iNewSpawnK );

			if ( iNewSpawnJ >= world.provider.getAverageGroundLevel() )
			{
				Material targetMaterial = world.getBlockMaterial( iNewSpawnI, iNewSpawnJ, iNewSpawnK );

				if ( targetMaterial == null || !targetMaterial.isLiquid() )
				{	        	
					player.setLocationAndAngles( (double)iNewSpawnI + 0.5D, (double)iNewSpawnJ + 1.5D, (double)iNewSpawnK + 0.5D, world.rand.nextFloat() * 360F, 0.0F);

					BumpPlayerPosUpwardsUntilValidSpawnReached( player );		        	

					player.m_lTimeOfLastSpawnAssignment = lTimeOfLastPlayerSpawnAssignment;

					player.m_HardcoreSpawnChunk = spawnPos;

					return true;	        		
				}
			}
		}

		return AssignPlayerToOldSpawnPos( world, player, spawnPos, lTimeOfLastPlayerSpawnAssignment );
	}

	private static boolean AssignPlayerToOldSpawnPos( World world, EntityPlayerMP player, ChunkCoordinates spawnPos, long lTimeOfLastPlayerSpawnAssignment )
	{
		int iSpawnI = MathHelper.floor_double( spawnPos.posX );
		int iSpawnK = MathHelper.floor_double( spawnPos.posZ );
		int iSpawnJ = world.getTopSolidOrLiquidBlock( iSpawnI, iSpawnK );

		player.setLocationAndAngles( (double)iSpawnI + 0.5F, (double)iSpawnJ + 1.5F, (double)iSpawnK + 0.5F, world.rand.nextFloat() * 360F, 0.0F);

		Material targetMaterial = world.getBlockMaterial( iSpawnI, iSpawnJ + 1, iSpawnK );

		if ( OffsetPlayerPositionUntilValidSpawn( world, player ) )
		{
			BumpPlayerPosUpwardsUntilValidSpawnReached( player );

			player.m_lTimeOfLastSpawnAssignment = lTimeOfLastPlayerSpawnAssignment;

			ChunkCoordinates newSpawnPos = new ChunkCoordinates( MathHelper.floor_double( player.posX ),
					MathHelper.floor_double( player.posY ), MathHelper.floor_double( player.posZ ) );

			player.m_HardcoreSpawnChunk = newSpawnPos;

			return true;
		}

		return false;
	}

	private static boolean OffsetPlayerPositionUntilValidSpawn( World world, EntityPlayerMP player )
	{
		int iSpawnI = MathHelper.floor_double( player.posX );
		int iSpawnK = MathHelper.floor_double( player.posZ );        

		for ( int iAttemptCount = 0; iAttemptCount < 20; iAttemptCount++ )
		{
			int iSpawnJ = world.getTopSolidOrLiquidBlock( iSpawnI, iSpawnK );

			Material targetMaterial = world.getBlockMaterial( iSpawnI, iSpawnJ, iSpawnK );

			if ( targetMaterial == null || !targetMaterial.isLiquid() )
			{
				player.setLocationAndAngles( (double)iSpawnI + 0.5D, player.posY, (double)iSpawnK + 0.5D, player.rotationYaw, player.rotationPitch);

				return true;
			}

			iSpawnI += world.rand.nextInt( 11 ) - 5;
			iSpawnK += world.rand.nextInt( 11 ) - 5;
		}

		return false;
	}

	private static void ReturnPlayerToOriginalSpawn( World world, EntityPlayerMP player )
	{
		ChunkCoordinates spawnPos = world.getSpawnPoint();

		int spawnI = spawnPos.posX;
		int spawnJ = spawnPos.posY;
		int spawnK = spawnPos.posZ;

		if ( !world.provider.hasNoSky && world.getWorldInfo().getGameType() != EnumGameType.ADVENTURE )
		{
			spawnI += world.rand.nextInt(20) - 10;
			spawnJ = world.getTopSolidOrLiquidBlock( spawnI, spawnK );
			spawnK += world.rand.nextInt(20) - 10;
		}

		player.setLocationAndAngles( (double)spawnI + 0.5D, (double)spawnJ + 1.5D, (double)spawnK + 0.5D, 0.0F, 0.0F );

		BumpPlayerPosUpwardsUntilValidSpawnReached( player );

		player.m_lTimeOfLastSpawnAssignment = 0;

		player.m_HardcoreSpawnChunk = null;        
	}

	private static void BumpPlayerPosUpwardsUntilValidSpawnReached( EntityPlayerMP player )
	{
		do
		{
			if ( player.posY <= 0.0D )
			{
				break;
			}

			player.setLocationAndAngles( player.posX, player.posY, player.posZ, player.rotationYaw, player.rotationPitch);

			if ( player.worldObj.getCollidingBoundingBoxes( player, player.boundingBox).isEmpty())
			{
				break;
			}

			player.posY++;
		}
		while (true);
	}

	public static boolean IsInLootedTempleRadius( World world, int iX, int iZ )
	{
		int iSpawnX = world.getWorldInfo().getSpawnX();
		int iSpawnZ = world.getWorldInfo().getSpawnZ();

		double dDeltaX = (double)( iSpawnX - iX );
		double dDeltaZ = (double)( iSpawnZ - iZ );

		double dDistSqFromSpawn = dDeltaX * dDeltaX + dDeltaZ * dDeltaZ;    	
		double dLootedRadius = GetLootedTempleRadius();    	

		return dDistSqFromSpawn < dLootedRadius * dLootedRadius;
	}
}
