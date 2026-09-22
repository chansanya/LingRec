const { createApp, ref, computed, onMounted, onBeforeUnmount, nextTick, h } = Vue;

const AppIcon = {
    props: {
        name: { type: String, required: true },
        size: { type: [Number, String], default: 18 },
        strokeWidth: { type: [Number, String], default: 2 }
    },
    setup(props) {
        return () => {
            const iconNode = lucide.icons[props.name] || lucide.icons.CircleHelp;
            return h('svg', {
                xmlns: 'http://www.w3.org/2000/svg',
                width: props.size,
                height: props.size,
                viewBox: '0 0 24 24',
                fill: 'none',
                stroke: 'currentColor',
                'stroke-width': props.strokeWidth,
                'stroke-linecap': 'round',
                'stroke-linejoin': 'round',
                'aria-hidden': 'true',
                class: 'lucide-icon'
            }, iconNode.map(([tag, attrs]) => h(tag, attrs)));
        };
    }
};

const app = createApp({
    setup() {
        const currentUserId = ref(null);
        const users = ref([]);
        const categories = ref([]);
        const recommendations = ref([]);
        const recommendationSessionId = ref(null);
        const recommendationCursor = ref(0);
        const recommendationTotal = ref(0);
        const recommendationHasMore = ref(false);
        const userProfile = ref([]);
        const userStats = ref({ VIEW: 0, LIKE: 0, FAVORITE: 0 });
        const viewBehaviors = ref([]);
        const likeBehaviors = ref([]);
        const favoriteBehaviors = ref([]);
        const activeBehaviorPanelKey = ref('VIEW');
        const behaviorRecordsLoading = ref(false);
        const likedResourceIds = ref(new Set());
        const favoritedResourceIds = ref(new Set());
        const viewedResourceIds = ref(new Set());
        const submittingActions = ref(new Set());
        const selectedParentCategory = ref('all');
        const selectedChildCategory = ref('all');
        const loading = ref(false);
        const loadingMoreRecommendations = ref(false);
        const showBackTop = ref(false);

        let chartInstance = null;
        let recommendationRequestSerial = 0;
        let profileRequestSerial = 0;
        let behaviorRequestSerial = 0;
        let loadMoreTimer = null;

        const behaviorPanels = computed(() => [
            { key: 'VIEW', title: '浏览', icon: 'Eye', items: viewBehaviors.value },
            { key: 'LIKE', title: '点赞', icon: 'Heart', items: likeBehaviors.value },
            { key: 'FAVORITE', title: '收藏', icon: 'Star', items: favoriteBehaviors.value }
        ]);

        const activeBehaviorPanel = computed(() =>
            behaviorPanels.value.find(panel => panel.key === activeBehaviorPanelKey.value)
            || behaviorPanels.value[0]
        );

        const currentUser = computed(() => {
            return users.value.find(user => user.id === currentUserId.value) || null;
        });

        const selectedParent = computed(() => {
            if (selectedParentCategory.value === 'all') return null;
            return categories.value.find(
                category => String(category.id) === String(selectedParentCategory.value)
            ) || null;
        });

        const childCategories = computed(() => {
            return selectedParent.value?.children || [];
        });

        const activeCategoryId = computed(() => {
            if (selectedChildCategory.value !== 'all') {
                return Number(selectedChildCategory.value);
            }
            if (selectedParentCategory.value !== 'all') {
                return Number(selectedParentCategory.value);
            }
            return null;
        });

        const categoryColorMap = {
            '游戏': '#E6A23C',
            '编程': '#67C23A',
            '设计': '#F56C6C',
            '音乐': '#909399',
            '影视': '#7C3AED'
        };

        const categoryTagTypeMap = {
            '游戏': 'warning',
            '编程': 'success',
            '设计': 'danger',
            '音乐': 'info',
            '影视': ''
        };

        const deduplicateResources = (items) => {
            const seen = new Set(
                recommendations.value.map(item => String(item.id))
            );
            return (Array.isArray(items) ? items : []).filter(item => {
                if (!item || item.id == null) return false;
                const resourceId = String(item.id);
                if (seen.has(resourceId)) return false;
                seen.add(resourceId);
                return true;
            });
        };

        const loadUsers = async () => {
            try {
                const response = await axios.get('/api/users');
                users.value = response.data;
                if (users.value.length > 0 && !currentUserId.value) {
                    await switchUser(users.value[0].id);
                }
            } catch (error) {
                console.error('Failed to load users', error);
                ElementPlus.ElMessage.error('无法加载用户列表');
            }
        };

        const loadCategories = async () => {
            try {
                const response = await axios.get('/api/categories');
                categories.value = response.data;
            } catch (error) {
                console.error('Failed to load categories', error);
                ElementPlus.ElMessage.error('无法加载资源分类');
            }
        };

        const switchUser = async (userId) => {
            currentUserId.value = userId;
            selectedParentCategory.value = 'all';
            selectedChildCategory.value = 'all';
            activeBehaviorPanelKey.value = 'VIEW';
            likedResourceIds.value = new Set();
            favoritedResourceIds.value = new Set();
            viewedResourceIds.value = new Set();
            submittingActions.value.clear();
            await Promise.all([
                loadRecommendations(null, userId),
                loadUserProfile(userId),
                loadBehaviors(userId)
            ]);
        };

        const loadRecommendations = async (
            categoryId = activeCategoryId.value,
            userId = currentUserId.value
        ) => {
            if (!userId) return;

            if (loadMoreTimer) {
                clearTimeout(loadMoreTimer);
                loadMoreTimer = null;
            }
            loadingMoreRecommendations.value = false;
            showBackTop.value = false;

            const requestSerial = ++recommendationRequestSerial;
            loading.value = true;
            try {
                const params = categoryId == null ? {} : { categoryId };
                const response = await axios.get(`/api/recommend/${userId}`, { params });
                if (requestSerial === recommendationRequestSerial && userId === currentUserId.value) {
                    const page = response.data;
                    recommendations.value = deduplicateResources(page.items || []);
                    recommendationSessionId.value = page.sessionId;
                    recommendationCursor.value = page.nextCursor || 0;
                    recommendationTotal.value = page.total || 0;
                    recommendationHasMore.value = !!page.hasMore;
                }
            } catch (error) {
                if (requestSerial === recommendationRequestSerial) {
                    console.error('Failed to load recommendations', error);
                    ElementPlus.ElMessage.error('加载推荐失败');
                }
            } finally {
                if (requestSerial === recommendationRequestSerial) {
                    loading.value = false;
                }
            }
        };

        const loadMoreRecommendations = async () => {
            const userId = currentUserId.value;
            if (
                !userId
                || loading.value
                || loadingMoreRecommendations.value
                || !recommendationHasMore.value
                || !recommendationSessionId.value
            ) {
                return;
            }

            loadingMoreRecommendations.value = true;
            const requestSerial = recommendationRequestSerial;
            try {
                const response = await axios.get(`/api/recommend/${userId}/page`, {
                    params: {
                        sessionId: recommendationSessionId.value,
                        cursor: recommendationCursor.value,
                        size: 20
                    }
                });

                if (requestSerial === recommendationRequestSerial && userId === currentUserId.value) {
                    const page = response.data;
                    const appended = deduplicateResources(page.items || []);
                    recommendations.value = [...recommendations.value, ...appended];
                    recommendationCursor.value = page.nextCursor || recommendationCursor.value;
                    recommendationTotal.value = page.total || recommendationTotal.value;
                    recommendationHasMore.value = !!page.hasMore;
                }
            } catch (error) {
                if (requestSerial === recommendationRequestSerial) {
                    console.error('Failed to load more recommendations', error);
                    ElementPlus.ElMessage.error('加载更多资源失败');
                }
            } finally {
                loadingMoreRecommendations.value = false;
            }
        };

        const handleResourceScroll = (event) => {
            const container = event.currentTarget;
            showBackTop.value = container.scrollTop > 150;
            const remaining = container.scrollHeight - container.scrollTop - container.clientHeight;
            if (remaining <= 120) {
                loadMoreRecommendations();
            }
        };

        const scrollToTop = () => {
            const container = document.querySelector('.resource-scroll');
            if (container) {
                container.scrollTo({ top: 0, behavior: 'smooth' });
            }
        };

        const loadUserProfile = async (userId = currentUserId.value) => {
            if (!userId) return;

            const requestSerial = ++profileRequestSerial;
            try {
                const response = await axios.get(`/api/users/${userId}/profile`);
                if (requestSerial !== profileRequestSerial || userId !== currentUserId.value) return;

                userProfile.value = response.data.profile || [];
                userStats.value = response.data.stats || { VIEW: 0, LIKE: 0, FAVORITE: 0 };
                likedResourceIds.value = new Set((response.data.likedResourceIds || []).map(Number));
                favoritedResourceIds.value = new Set((response.data.favoritedResourceIds || []).map(Number));
                viewedResourceIds.value = new Set((response.data.viewedResourceIds || []).map(Number));
                await nextTick();
                renderChart();
            } catch (error) {
                if (requestSerial === profileRequestSerial) {
                    console.error('Failed to load user profile', error);
                }
            }
        };

        const loadBehaviors = async (userId = currentUserId.value) => {
            if (!userId) return;

            const requestSerial = ++behaviorRequestSerial;
            behaviorRecordsLoading.value = true;
            try {
                const [views, likes, favorites] = await Promise.all([
                    axios.get(`/api/behaviors/${userId}`, { params: { limit: 20, action: 'VIEW' } }),
                    axios.get(`/api/behaviors/${userId}`, { params: { limit: 20, action: 'LIKE' } }),
                    axios.get(`/api/behaviors/${userId}`, { params: { limit: 20, action: 'FAVORITE' } })
                ]);
                if (requestSerial === behaviorRequestSerial && userId === currentUserId.value) {
                    viewBehaviors.value = views.data;
                    likeBehaviors.value = likes.data;
                    favoriteBehaviors.value = favorites.data;
                }
            } catch (error) {
                if (requestSerial === behaviorRequestSerial) {
                    console.error('Failed to load behaviors', error);
                    ElementPlus.ElMessage.error('加载行为记录失败');
                }
            } finally {
                if (requestSerial === behaviorRequestSerial) {
                    behaviorRecordsLoading.value = false;
                }
            }
        };

        const recordBehavior = async (resourceId, action) => {
            const userId = currentUserId.value;
            if (!userId || !resourceId || !action) return;

            const numericResourceId = Number(resourceId);
            const actionKey = `${numericResourceId}_${action}`;
            if (submittingActions.value.has(actionKey)) return;

            submittingActions.value.add(actionKey);
            try {
                const response = await axios.post('/api/behaviors', {
                    userId,
                    resourceId: numericResourceId,
                    action
                });
                if (userId !== currentUserId.value) return;

                const isCancelled = response.data?.status === 'CANCELLED';
                const actionLabel = getActionLabel(action);

                if (action === 'LIKE') {
                    if (isCancelled) {
                        likedResourceIds.value.delete(numericResourceId);
                    } else {
                        likedResourceIds.value.add(numericResourceId);
                    }
                } else if (action === 'FAVORITE') {
                    if (isCancelled) {
                        favoritedResourceIds.value.delete(numericResourceId);
                    } else {
                        favoritedResourceIds.value.add(numericResourceId);
                    }
                } else if (action === 'VIEW') {
                    viewedResourceIds.value.add(numericResourceId);
                }

                const target = recommendations.value.find(item => Number(item.id) === numericResourceId);
                if (target && response.data?.heat != null) {
                    target.heat = response.data.heat;
                }

                ElementPlus.ElMessage({
                    message: isCancelled ? `已取消${actionLabel}` : `${actionLabel}已记录`,
                    type: isCancelled ? 'info' : 'success',
                    duration: 1600
                });

                await Promise.all([
                    loadUserProfile(userId),
                    loadBehaviors(userId)
                ]);
            } catch (error) {
                console.error('Failed to record behavior', error);
                ElementPlus.ElMessage.error(error.response?.data?.message || '操作失败');
            } finally {
                submittingActions.value.delete(actionKey);
            }
        };

        const handleResourceClick = (item) => {
            recordBehavior(item.id, 'VIEW');
        };

        const handleParentTabClick = (tab) => {
            const paneName = String(tab.paneName ?? tab.props?.name ?? 'all');
            selectedParentCategory.value = paneName;
            selectedChildCategory.value = 'all';
            loadRecommendations(paneName === 'all' ? null : Number(paneName));
        };

        const selectChildCategory = (categoryId) => {
            selectedChildCategory.value = String(categoryId);
            const requestedCategoryId = categoryId === 'all'
                ? Number(selectedParentCategory.value)
                : Number(categoryId);
            loadRecommendations(requestedCategoryId);
        };

        const renderChart = () => {
            const chartDom = document.getElementById('interest-chart');
            if (!chartDom) return;

            if (!chartInstance) {
                chartInstance = echarts.init(chartDom);
            }

            const sortedProfile = [...userProfile.value].sort((a, b) => a.score - b.score);
            const categoryNames = sortedProfile.map(item => item.categoryName);
            const scores = sortedProfile.map(item => item.score);
            const colors = sortedProfile.map(
                item => categoryColorMap[item.parentCategoryName] || '#409EFF'
            );

            chartInstance.setOption({
                tooltip: {
                    trigger: 'axis',
                    axisPointer: { type: 'shadow' }
                },
                grid: {
                    top: 10,
                    bottom: 20,
                    left: 70,
                    right: 35
                },
                xAxis: {
                    type: 'value',
                    max: 100,
                    splitLine: { show: false }
                },
                yAxis: {
                    type: 'category',
                    data: categoryNames,
                    axisLabel: {
                        interval: 0,
                        width: 60,
                        overflow: 'truncate'
                    }
                },
                series: [{
                    type: 'bar',
                    data: scores.map((score, index) => ({
                        value: Number(score.toFixed(1)),
                        itemStyle: { color: colors[index] }
                    })),
                    label: {
                        show: true,
                        position: 'right',
                        formatter: '{c}'
                    }
                }]
            }, true);
        };

        const formatDate = (dateStr) => {
            if (!dateStr) return '';
            return new Date(dateStr).toLocaleDateString('zh-CN');
        };

        const formatTime = (dateStr) => {
            if (!dateStr) return '';
            const date = new Date(dateStr);
            return `${date.getMonth() + 1}/${date.getDate()} ${date.getHours()}:${String(date.getMinutes()).padStart(2, '0')}`;
        };

        const getBehaviorType = (action) => {
            if (action === 'VIEW') return 'info';
            if (action === 'LIKE') return 'danger';
            if (action === 'FAVORITE') return 'warning';
            return 'primary';
        };

        const getActionLabel = (action) => {
            if (action === 'VIEW') return '浏览';
            if (action === 'LIKE') return '点赞';
            if (action === 'FAVORITE') return '收藏';
            return action;
        };

        const getCategoryTagType = (parentCategoryName) => {
            return categoryTagTypeMap[parentCategoryName] || '';
        };

        const hasInteracted = (resourceId, action) => {
            const id = Number(resourceId);
            if (action === 'LIKE') {
                return likedResourceIds.value.has(id);
            }
            if (action === 'FAVORITE') {
                return favoritedResourceIds.value.has(id);
            }
            if (action === 'VIEW') {
                return viewedResourceIds.value.has(id);
            }
            return false;
        };

        const handleResize = () => {
            if (chartInstance) chartInstance.resize();
        };

        onMounted(async () => {
            window.addEventListener('resize', handleResize);
            await Promise.all([loadCategories(), loadUsers()]);
        });

        onBeforeUnmount(() => {
            window.removeEventListener('resize', handleResize);
            if (loadMoreTimer) clearTimeout(loadMoreTimer);
            if (chartInstance) chartInstance.dispose();
        });

        return {
            currentUserId,
            users,
            categories,
            currentUser,
            childCategories,
            recommendations,
            recommendationTotal,
            recommendationHasMore,
            userStats,
            behaviorPanels,
            activeBehaviorPanel,
            activeBehaviorPanelKey,
            behaviorRecordsLoading,
            selectedParentCategory,
            selectedChildCategory,
            loading,
            loadingMoreRecommendations,
            switchUser,
            loadRecommendations,
            recordBehavior,
            handleParentTabClick,
            selectChildCategory,
            handleResourceClick,
            handleResourceScroll,
            showBackTop,
            scrollToTop,
            formatDate,
            formatTime,
            getBehaviorType,
            getActionLabel,
            getCategoryTagType,
            hasInteracted,
            likedResourceIds,
            favoritedResourceIds,
            viewedResourceIds
        };
    }
});

app.component('app-icon', AppIcon);
app.use(ElementPlus);
app.mount('#app');
