package com.curso.android.module4.cityspots.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.imageLoader
import coil.request.ImageRequest
import com.curso.android.module4.cityspots.data.entity.SpotEntity
import com.curso.android.module4.cityspots.ui.viewmodel.MapViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import org.koin.androidx.compose.koinViewModel

@Composable
fun MapScreen(
    onNavigateToCamera: () -> Unit,
    viewModel: MapViewModel = koinViewModel()
) {
    val spots by viewModel.spots.collectAsState()
    val userLocation by viewModel.userLocation.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var spotToDelete by remember { mutableStateOf<SpotEntity?>(null) }
    var hasCenteredMap by remember { mutableStateOf(false) }

    // Diálogo de confirmación de eliminación
    if (spotToDelete != null) {
        AlertDialog(
            onDismissRequest = { spotToDelete = null },
            title = { Text("¿Eliminar Spot?") },
            text = { Text("Esta acción borrará permanentemente el spot y su foto.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        spotToDelete?.let { viewModel.deleteSpot(it.id) }
                        spotToDelete = null
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { spotToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Pre-carga de imágenes
    LaunchedEffect(spots) {
        spots.forEach { spot ->
            val request = ImageRequest.Builder(context)
                .data(spot.imageUri.toUri())
                .build()
            context.imageLoader.enqueue(request)
        }
    }

    // Carga inicial
    LaunchedEffect(Unit) {
        viewModel.loadUserLocation()
    }

    // Manejo de errores
    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(14.6349, -90.5069), 12f)
    }

    // Animación automática de cámara al recibir ubicación
    LaunchedEffect(userLocation) {
        userLocation?.let { location ->
            if (!hasCenteredMap) {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(location, 15f))
                hasCenteredMap = true
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCamera,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar Spot")
            }
        }
    ) { paddingValues ->
        var selectedSpot by remember { mutableStateOf<SpotEntity?>(null) }

        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            SpotMap(
                spots = spots,
                cameraPositionState = cameraPositionState,
                onSpotClick = { spot -> selectedSpot = spot },
                onMapClick = { selectedSpot = null },
                onSpotLongClick = { spot -> spotToDelete = spot } // Se agregó este parámetro faltante
            )

            selectedSpot?.let { spot ->
                SpotInfoCard(
                    spot = spot,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    onDelete = {
                        spotToDelete = spot
                        selectedSpot = null // Ocultar card al intentar borrar
                    }
                )
            }

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun SpotMap(
    spots: List<SpotEntity>,
    cameraPositionState: CameraPositionState,
    onSpotClick: (SpotEntity) -> Unit,
    onMapClick: () -> Unit,
    onSpotLongClick: (SpotEntity) -> Unit
) {
    val mapProperties = remember {
        MapProperties(isMyLocationEnabled = true, isBuildingEnabled = true)
    }
    val mapUiSettings = remember {
        MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = true)
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = mapProperties,
        uiSettings = mapUiSettings,
        onMapClick = { onMapClick() }
    ) {
        spots.forEach { spot ->
            val markerState = rememberMarkerState(
                key = spot.id.toString(),
                position = LatLng(spot.latitude, spot.longitude)
            )

            Marker(
                state = markerState,
                title = spot.title,
                onClick = {
                    onSpotClick(spot)
                    true
                },
                onInfoWindowLongClick = { // Long click en la ventana de info
                    onSpotLongClick(spot)
                }
            )
        }
    }
}

@Composable
private fun SpotInfoCard(
    spot: SpotEntity,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SubcomposeAsyncImage(
                model = spot.imageUri.toUri(),
                contentDescription = spot.title,
                modifier = Modifier.size(280.dp, 160.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                    }
                },
                success = { SubcomposeAsyncImageContent() }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = spot.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "📍 ${String.format("%.4f", spot.latitude)}, ${String.format("%.4f", spot.longitude)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TextButton(onClick = onDelete) {
                Text("Eliminar Spot", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}