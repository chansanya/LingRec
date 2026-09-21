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
        const userProfile = ref([]);
        const userStats = ref({ VIEW: 0, LIKE: 0, FAVORITE: 0 });
        const recentBehaviors = ref([]);
        const selectedParentCategory = ref('all');
        const selectedChildCategory = ref('all');
        const loading = ref(false);

        let chartInstance = null;
        let recommendationRequestSerial = 0;
        let profileRequestSerial = 0;
        let behaviorRequestSerial = 0;

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

            const requestSerial = ++recommendationRequestSerial;
            loading.value = true;
            try {
                const params = categoryId == null ? {} : { categoryId };
                const response = await axios.get(`/api/recommend/${userId}`, { params });
                if (requestSerial === recommendationRequestSerial && userId === currentUserId.value) {
                    recommendations.value = response.data;
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

        const loadUserProfile = async (userId = currentUserId.value) => {
            if (!userId) return;

            const requestSerial = ++profileRequestSerial;
            try {
                const response = await axios.get(`/api/users/${userId}/profile`);
                if (requestSerial !== profileRequestSerial || userId !== currentUserId.value) return;

                userProfile.value = response.data.profile || [];
                userStats.value = response.data.stats || { VIEW: 0, LIKE: 0, FAVORITE: 0 };
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
            try {
                const response = await axios.get(`/api/behaviors/${userId}`, {
                    params: { limit: 20 }
                });
                if (requestSerial === behaviorRequestSerial && userId === currentUserId.value) {
                    recentBehaviors.value = response.data;
                }
            } catch (error) {
                if (requestSerial === behaviorRequestSerial) {
                    console.error('Failed to load behaviors', error);
                }
            }
        };

        const recordBehavior = async (resourceId, action) => {
            const userId = currentUserId.value;
            if (!userId) return;

            try {
                await axios.post('/api/behaviors', { userId, resourceId, action });
                if (userId !== currentUserId.value) return;

                ElementPlus.ElMessage({
                    message: `${getActionLabel(action)}已记录`,
                    type: 'success',
                    duration: 1600
                });

                await Promise.all([
                    loadUserProfile(userId),
                    loadBehaviors(userId),
                    loadRecommendations(activeCategoryId.value, userId)
                ]);
            } catch (error) {
                console.error('Failed to record behavior', error);
                ElementPlus.ElMessage.error(error.response?.data?.message || '行为记录失败');
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
            return recentBehaviors.value.some(
                behavior => behavior.resourceId === resourceId && behavior.action === action
            );
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
            if (chartInstance) chartInstance.dispose();
        });

        return {
            currentUserId,
            users,
            categories,
            currentUser,
            childCategories,
            recommendations,
            userStats,
            recentBehaviors,
            selectedParentCategory,
            selectedChildCategory,
            loading,
            switchUser,
            loadRecommendations,
            recordBehavior,
            handleParentTabClick,
            selectChildCategory,
            handleResourceClick,
            formatDate,
            formatTime,
            getBehaviorType,
            getActionLabel,
            getCategoryTagType,
            hasInteracted
        };
    }
});

app.component('app-icon', AppIcon);
app.use(ElementPlus);
app.mount('#app');
